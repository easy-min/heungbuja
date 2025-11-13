package com.heungbuja.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 회원가입 요청
 * role은 서버에서 AdminRole.ADMIN으로 고정
 */
@Getter
@NoArgsConstructor
public class AdminRegisterRequest extends BaseAdminRequest {

    public AdminRegisterRequest(String username, String password, String facilityName,
                                String contact, String email) {
        super(username, password, facilityName, contact, email);
    }
}
