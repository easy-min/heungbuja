package com.heungbuja.admin.dto;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.entity.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class AdminResponse {

    private Long id;
    private String username;
    private String facilityName;
    private String contact;
    private String email;
    private AdminRole role;
    private LocalDateTime createdAt;

    public static AdminResponse from(Admin admin) {
        return AdminResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .facilityName(admin.getFacilityName())
                .contact(admin.getContact())
                .email(admin.getEmail())
                .role(admin.getRole())
                .createdAt(admin.getCreatedAt())
                .build();
    }
}
