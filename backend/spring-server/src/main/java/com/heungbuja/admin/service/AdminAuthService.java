package com.heungbuja.admin.service;

import com.heungbuja.admin.dto.AdminLoginRequest;
import com.heungbuja.admin.dto.AdminRegisterRequest;
import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.entity.AdminRole;
import com.heungbuja.auth.dto.TokenResponse;
import com.heungbuja.auth.entity.RefreshToken;
import com.heungbuja.auth.repository.RefreshTokenRepository;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Admin 인증 서비스
 * - 회원가입, 로그인, 토큰 생성 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final int REFRESH_TOKEN_EXPIRE_DAYS = 7;

    /**
     * 관리자 회원가입
     * role은 자동으로 ADMIN으로 설정
     */
    @Transactional
    public TokenResponse register(AdminRegisterRequest request) {
        Admin admin = adminService.createAdmin(
                request.getUsername(),
                request.getPassword(),
                request.getFacilityName(),
                request.getContact(),
                request.getEmail(),
                AdminRole.ADMIN  // 일반 회원가입은 ADMIN 고정
        );

        return generateAndSaveTokens(admin);
    }

    /**
     * 관리자 로그인
     */
    @Transactional
    public TokenResponse login(AdminLoginRequest request) {
        Admin admin = adminService.findByUsername(request.getUsername());

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("Admin login success: id={}, username={}", admin.getId(), admin.getUsername());

        return generateAndSaveTokens(admin);
    }

    /**
     * 토큰 생성 및 RefreshToken DB 저장
     */
    private TokenResponse generateAndSaveTokens(Admin admin) {
        String role = "ROLE_" + admin.getRole().name();
        String accessToken = jwtUtil.generateAccessToken(admin.getId(), admin.getUsername(), role);
        String refreshToken = jwtUtil.generateRefreshToken(admin.getId(), admin.getUsername(), role);

        // RefreshToken DB 저장
        saveRefreshToken(admin, refreshToken);

        return TokenResponse.of(accessToken, refreshToken, admin.getId(), role);
    }

    /**
     * RefreshToken 엔티티 생성 및 저장
     */
    private void saveRefreshToken(Admin admin, String token) {
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .token(token)
                .admin(admin)
                .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRE_DAYS))
                .build();

        refreshTokenRepository.save(refreshTokenEntity);
        log.debug("RefreshToken saved for admin: id={}", admin.getId());
    }
}
