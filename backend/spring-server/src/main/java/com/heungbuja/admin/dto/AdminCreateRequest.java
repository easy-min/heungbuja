package com.heungbuja.admin.dto;

import com.heungbuja.admin.entity.AdminRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SUPER_ADMIN이 새로운 관리자를 생성하는 요청
 * role을 지정할 수 있음
 */
@Getter
@NoArgsConstructor
public class AdminCreateRequest extends BaseAdminRequest {

    @NotNull(message = "Role is required")
    private AdminRole role;

    public AdminCreateRequest(String username, String password, String facilityName,
                             String contact, String email, AdminRole role) {
        super(username, password, facilityName, contact, email);
        this.role = role;
    }
}
