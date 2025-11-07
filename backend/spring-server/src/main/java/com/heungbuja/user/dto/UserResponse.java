package com.heungbuja.user.dto;

import com.heungbuja.user.entity.User;
import com.heungbuja.user.entity.User.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String name;
    private LocalDate birthDate;
    private Gender gender;
    private String medicalNotes;
    private String emergencyContact;
    private Long deviceId;
    private Boolean isActive;
    private Long adminId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RecentActivityDto> recentActivities;  // 최근 활동 내역

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .medicalNotes(user.getMedicalNotes())
                .emergencyContact(user.getEmergencyContact())
                .deviceId(user.getDevice() != null ? user.getDevice().getId() : null)
                .isActive(user.getIsActive())
                .adminId(user.getAdmin().getId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
