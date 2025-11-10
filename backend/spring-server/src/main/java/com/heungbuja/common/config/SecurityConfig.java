package com.heungbuja.common.config;

import com.heungbuja.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Configuration
@Slf4j
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final Environment env;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // 현재 활성화된 프로파일 중에 'test'가 포함되어 있는지 확인
                    boolean isTestProfile = Arrays.asList(env.getActiveProfiles()).contains("test");

                    if (isTestProfile) {
                        // test 프로파일일 경우 모든 요청 허용
                        log.warn("==[ 경고: TEST 프로파일 활성화. 모든 API 요청이 허용됩니다. ]==");
                        auth.anyRequest().permitAll();
                    } else {
                        // test 프로파일이 아닐 경우 기존의 보안 규칙 적용
                        auth
                                // Public endpoints
                                .requestMatchers("/admins/register", "/admins/login").permitAll()
                                .requestMatchers("/auth/device", "/auth/refresh").permitAll()
                                .requestMatchers("/health").permitAll()
                                .requestMatchers("/media/test", "/media/test/**").permitAll()
                                .requestMatchers("/ws/**").permitAll()

                                // Voice & Commands - JWT 인증 필요 (보안 강화)
                                .requestMatchers("/commands/**").authenticated()

                                // Emergency (Public - 응급 상황은 인증 없이 허용)
                                .requestMatchers("/emergency").permitAll()
                                .requestMatchers("/emergency/*/cancel").permitAll()
                                .requestMatchers("/emergency/*/confirm").permitAll()

                                // SUPER_ADMIN only (관리자 생성 및 전체 조회)
                                .requestMatchers("/admins").hasAuthority("ROLE_SUPER_ADMIN")

                                // ADMIN and SUPER_ADMIN (기기 및 어르신 관리)
                                .requestMatchers("/admins/devices/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                                .requestMatchers("/admins/users/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_USER")
                                .requestMatchers("/emergency/admins/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                                .anyRequest().authenticated();
                    }
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        // 커스텀 응답 헤더를 브라우저에서 읽을 수 있도록 노출
        configuration.setExposedHeaders(Arrays.asList(
                "X-Success",
                "X-Intent",
                "X-Response-Text",
                "X-Song-Title",
                "X-Song-Artist",
                "X-Error-Code"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
