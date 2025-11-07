package com.heungbuja.admin.service;

import com.heungbuja.admin.dto.AdminCreateRequest;
import com.heungbuja.admin.dto.AdminLoginRequest;
import com.heungbuja.admin.dto.AdminRegisterRequest;
import com.heungbuja.admin.dto.AdminResponse;
import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.entity.AdminRole;
import com.heungbuja.admin.repository.AdminRepository;
import com.heungbuja.auth.dto.TokenResponse;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public TokenResponse register(AdminRegisterRequest request) {
        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new CustomException(ErrorCode.ADMIN_ALREADY_EXISTS);
        }

        Admin admin = Admin.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .facilityName(request.getFacilityName())
                .contact(request.getContact())
                .email(request.getEmail())
                .role(AdminRole.ADMIN)  // 일반 회원가입은 ADMIN
                .build();

        Admin savedAdmin = adminRepository.save(admin);

        String role = "ROLE_" + savedAdmin.getRole().name();
        String accessToken = jwtUtil.generateAccessToken(savedAdmin.getId(), savedAdmin.getUsername(), role);
        String refreshToken = jwtUtil.generateRefreshToken(savedAdmin.getId(), savedAdmin.getUsername(), role);

        return TokenResponse.of(accessToken, refreshToken, savedAdmin.getId(), role);
    }

    @Transactional
    public AdminResponse createAdmin(Long requesterId, AdminCreateRequest request) {
        // 요청자가 SUPER_ADMIN인지 확인
        Admin requester = findById(requesterId);
        if (requester.getRole() != AdminRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.FORBIDDEN, "Only SUPER_ADMIN can create new admins");
        }

        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new CustomException(ErrorCode.ADMIN_ALREADY_EXISTS);
        }

        Admin admin = Admin.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .facilityName(request.getFacilityName())
                .contact(request.getContact())
                .email(request.getEmail())
                .role(request.getRole())
                .build();

        Admin savedAdmin = adminRepository.save(admin);
        return AdminResponse.from(savedAdmin);
    }

    public TokenResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String role = "ROLE_" + admin.getRole().name();
        String accessToken = jwtUtil.generateAccessToken(admin.getId(), admin.getUsername(), role);
        String refreshToken = jwtUtil.generateRefreshToken(admin.getId(), admin.getUsername(), role);

        return TokenResponse.of(accessToken, refreshToken, admin.getId(), role);
    }

    public List<AdminResponse> getAllAdmins(Long requesterId) {
        // 요청자가 SUPER_ADMIN인지 확인
        Admin requester = findById(requesterId);
        if (requester.getRole() != AdminRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.FORBIDDEN, "오직 슈퍼 어드민만이 모든 어드민을 볼 수 있습니다.");
        }

        return adminRepository.findAll().stream()
                .map(AdminResponse::from)
                .collect(Collectors.toList());
    }

    public void validateAdminAccess(Long requesterId, Long targetAdminId) {
        Admin requester = findById(requesterId);

        // SUPER_ADMIN은 모든 데이터 접근 가능
        if (requester.getRole() == AdminRole.SUPER_ADMIN) {
            return;
        }

        // 일반 ADMIN은 자신의 데이터만 접근 가능
        if (!requesterId.equals(targetAdminId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "Access denied to other admin's data");
        }
    }

    public Admin findById(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
    }
}
