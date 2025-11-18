package com.heungbuja.common.security;

import com.heungbuja.admin.entity.AdminRole;
import com.heungbuja.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = extractTokenFromRequest(request);

            if (token != null && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                String role = jwtUtil.getRoleFromToken(token);

                log.info("🔐 JWT 인증 - userId: {}, username: {}, role from token: '{}'", userId, username, role);

                // Spring Security는 ROLE_ prefix를 기대함
                String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;

                log.info("🎭 최종 권한: '{}', 요청 URI: {}", authority, request.getRequestURI());

                // Admin인 경우 AdminPrincipal 사용 (타입 안전)
                Object principal;
                if (authority.equals("ROLE_ADMIN")) {
                    principal = new AdminPrincipal(userId, username, AdminRole.ADMIN);
                } else if (authority.equals("ROLE_SUPER_ADMIN")) {
                    principal = new AdminPrincipal(userId, username, AdminRole.SUPER_ADMIN);
                } else {
                    // User 로그인 (기존 방식: userId만 전달)
                    principal = userId;
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(authority))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // JWT 처리 중 예외 발생 시 로깅하고 인증 없이 진행
            logger.error("JWT authentication failed", e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
