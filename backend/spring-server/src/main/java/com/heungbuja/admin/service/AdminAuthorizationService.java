package com.heungbuja.admin.service;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.entity.AdminRole;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Admin 권한 검증 서비스
 * - SUPER_ADMIN 권한 체크
 * - 데이터 접근 권한 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthorizationService {

    private final AdminService adminService;

    /**
     * SUPER_ADMIN 권한 필요
     * SUPER_ADMIN이 아니면 예외 발생
     */
    public void requireSuperAdmin(Long adminId) {
        Admin admin = adminService.findById(adminId);

        if (admin.getRole() != AdminRole.SUPER_ADMIN) {
            log.warn("SUPER_ADMIN 권한 필요: adminId={}, role={}", adminId, admin.getRole());
            throw new CustomException(ErrorCode.FORBIDDEN, "SUPER_ADMIN 권한이 필요합니다");
        }
    }

    /**
     * Admin 데이터 접근 권한 검증
     * - SUPER_ADMIN은 모든 데이터 접근 가능
     * - 일반 ADMIN은 자신의 데이터만 접근 가능
     */
    public void requireAdminAccess(Long requesterId, Long targetAdminId) {
        Admin requester = adminService.findById(requesterId);

        // SUPER_ADMIN은 모든 데이터 접근 가능
        if (requester.getRole() == AdminRole.SUPER_ADMIN) {
            return;
        }

        // 일반 ADMIN은 자신의 데이터만 접근 가능
        if (!requesterId.equals(targetAdminId)) {
            log.warn("다른 관리자 데이터 접근 시도: requesterId={}, targetId={}", requesterId, targetAdminId);
            throw new CustomException(ErrorCode.FORBIDDEN, "다른 관리자의 데이터에 접근할 수 없습니다");
        }
    }
}
