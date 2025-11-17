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

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // OPTIONS 요청은 모두 허용 (CORS preflight)
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                        // Public endpoints
                        .requestMatchers("/admins/register", "/admins/login").permitAll()
                        .requestMatchers("/auth/device", "/auth/refresh").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/media/test", "/media/test/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // Admin HTML 페이지 (정적 파일)
                        .requestMatchers("/test-admin.html", "/test-admin-prod.html", "/test-admin-prod-backup.html").permitAll()

                        // Voice & Commands
                        .requestMatchers("/commands/tts/**").permitAll()  // TTS 다운로드는 인증 불필요
                        .requestMatchers("/commands/**").authenticated()  // 명령 처리는 JWT 필요

                        // Emergency (Public - 응급 상황은 인증 없이 허용)
                        .requestMatchers("/emergency").permitAll()
                        .requestMatchers("/emergency/*/cancel").permitAll()
                        .requestMatchers("/emergency/*/confirm").permitAll()

                        // ADMIN and SUPER_ADMIN (구체적인 경로를 먼저 매칭)
                        .requestMatchers("/admins/songs", "/admins/songs/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/admins/devices", "/admins/devices/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/admins/users", "/admins/users/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_USER")
                        .requestMatchers("/admins/activity-logs", "/admins/activity-logs/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                        .requestMatchers("/emergency/admins", "/emergency/admins/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                        // SUPER_ADMIN only (관리자 생성 및 전체 조회 - 마지막에)
                        .requestMatchers("/admins").hasAuthority("ROLE_SUPER_ADMIN")
                        .requestMatchers("/admins/**").hasAuthority("ROLE_SUPER_ADMIN")

                        .requestMatchers("/game/**").permitAll()  // ---- 우선 game 요청 허용

                        .anyRequest().authenticated()
                )
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

