package com.heungbuja.admin.service;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.entity.AdminRole;
import com.heungbuja.admin.repository.AdminRepository;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin 엔티티 CRUD 서비스
 * 인증/권한 검증은 AdminAuthService, AdminAuthorizationService 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Admin 생성 (공통 로직)
     */
    @Transactional
    public Admin createAdmin(String username, String password, String facilityName,
                            String contact, String email, AdminRole role) {
        validateUniqueUsername(username);

        Admin admin = Admin.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .facilityName(facilityName)
                .contact(contact)
                .email(email)
                .role(role)
                .build();

        Admin savedAdmin = adminRepository.save(admin);
        log.info("Admin created: id={}, username={}, role={}", savedAdmin.getId(), savedAdmin.getUsername(), savedAdmin.getRole());

        return savedAdmin;
    }

    /**
     * Admin 삭제 (의존성 체크 포함)
     */
    @Transactional
    public void deleteAdmin(Long adminId) {
        Admin admin = findById(adminId);

        // Device나 User가 연결되어 있으면 삭제 불가
        if (admin.hasDependencies()) {
            throw new CustomException(ErrorCode.ADMIN_HAS_DEPENDENCIES,
                    "관리 중인 기기나 사용자가 있어 삭제할 수 없습니다");
        }

        adminRepository.delete(admin);
        log.info("Admin deleted: id={}, username={}", adminId, admin.getUsername());
    }

    /**
     * ID로 Admin 조회
     */
    public Admin findById(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
    }

    /**
     * Username으로 Admin 조회
     */
    public Admin findByUsername(String username) {
        return adminRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND));
    }

    /**
     * 모든 Admin 조회 (페이징)
     */
    public Page<Admin> findAll(Pageable pageable) {
        return adminRepository.findAll(pageable);
    }

    /**
     * Username 중복 검증
     */
    private void validateUniqueUsername(String username) {
        if (adminRepository.existsByUsername(username)) {
            throw new CustomException(ErrorCode.ADMIN_ALREADY_EXISTS);
        }
    }
}
