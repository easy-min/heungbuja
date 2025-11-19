package com.heungbuja.common.security;

import com.heungbuja.admin.entity.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Admin 인증 정보를 담는 Principal 클래스
 * Type-safe하게 Authentication에서 Admin 정보를 추출하기 위함
 */
@Getter
@AllArgsConstructor
public class AdminPrincipal {

    private Long id;
    private String username;
    private AdminRole role;

    public boolean isSuperAdmin() {
        return role == AdminRole.SUPER_ADMIN;
    }

    public boolean isAdmin() {
        return role == AdminRole.ADMIN;
    }
}
