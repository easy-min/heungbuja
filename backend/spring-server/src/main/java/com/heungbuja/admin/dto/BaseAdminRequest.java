package com.heungbuja.admin.dto;

import com.heungbuja.common.validation.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Admin 생성/수정 요청의 공통 필드
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseAdminRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @PasswordPolicy
    private String password;

    @NotBlank(message = "Facility name is required")
    @Size(max = 100, message = "Facility name must not exceed 100 characters")
    private String facilityName;

    @Size(max = 20, message = "Contact must not exceed 20 characters")
    private String contact;

    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;
}
