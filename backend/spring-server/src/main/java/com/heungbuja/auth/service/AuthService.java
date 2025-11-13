package com.heungbuja.auth.service;

import com.heungbuja.auth.dto.DeviceLoginRequest;
import com.heungbuja.auth.dto.TokenRefreshRequest;
import com.heungbuja.auth.dto.TokenResponse;
import com.heungbuja.auth.entity.RefreshToken;
import com.heungbuja.auth.repository.RefreshTokenRepository;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.common.util.JwtUtil;
import com.heungbuja.device.entity.Device;
import com.heungbuja.device.entity.Device.DeviceStatus;
import com.heungbuja.device.service.DeviceService;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final DeviceService deviceService;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public TokenResponse deviceLogin(DeviceLoginRequest request) {
        // 1. 시리얼 번호로 기기 조회
        Device device = deviceService.findBySerialNumber(request.getSerialNumber());

        // 2. 기기 상태가 ACTIVE인지 확인
        if (device.getStatus() != DeviceStatus.ACTIVE) {
            throw new CustomException(ErrorCode.DEVICE_NOT_ACTIVE,
                    "Device is not active. Please contact administrator.");
        }

        // 3. 기기에 매칭된 활성 사용자 조회
        User user = userService.findByDeviceId(device.getId());

        if (!user.getIsActive()) {
            throw new CustomException(ErrorCode.USER_NOT_ACTIVE);
        }

        // 4. JWT 토큰 발급
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getName(), "ROLE_USER");
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getName(), "ROLE_USER");

        // 5. Refresh Token 저장
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtUtil.getRefreshTokenValidityInMillis() / 1000);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .device(device)
                .expiresAt(expiresAt)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        // 6. 기기 마지막 활성 시간 업데이트
        deviceService.updateLastActiveAt(device.getId());

        return TokenResponse.of(accessToken, refreshToken, user.getId(), "ROLE_USER");
    }

    @Transactional
    public TokenResponse refresh(TokenRefreshRequest request) {
        // 1. Refresh Token 조회
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 2. 만료 확인
        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        }

        // 3. User 또는 Admin 확인 및 Access Token 발급
        String role = jwtUtil.getRoleFromToken(refreshToken.getToken());

        // User 로그인인 경우
        if (refreshToken.getUser() != null) {
            User user = refreshToken.getUser();
            if (!user.getIsActive()) {
                refreshTokenRepository.delete(refreshToken);
                throw new CustomException(ErrorCode.USER_NOT_ACTIVE);
            }

            String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getName(), role);
            return TokenResponse.of(newAccessToken, refreshToken.getToken(), user.getId(), role);
        }

        // Admin 로그인인 경우
        else if (refreshToken.getAdmin() != null) {
            var admin = refreshToken.getAdmin();
            // Admin은 항상 활성 상태로 가정 (필요시 isActive 필드 추가 가능)

            String newAccessToken = jwtUtil.generateAccessToken(admin.getId(), admin.getUsername(), role);
            return TokenResponse.of(newAccessToken, refreshToken.getToken(), admin.getId(), role);
        }

        // User도 Admin도 아닌 경우 (데이터 무결성 오류)
        else {
            refreshTokenRepository.delete(refreshToken);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND, "Invalid refresh token: no associated user or admin");
        }
    }

    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
