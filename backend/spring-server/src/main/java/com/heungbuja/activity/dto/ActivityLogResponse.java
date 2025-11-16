package com.heungbuja.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heungbuja.activity.entity.UserActivityLog;
import com.heungbuja.activity.enums.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 활동 로그 응답 DTO (관리자 페이지용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogResponse {

    private Long id;
    private Long userId;
    private String userName;
    private ActivityType activityType;
    private String activitySummary;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Entity -> DTO 변환
     */
    public static ActivityLogResponse from(UserActivityLog log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .userId(log.getUser().getId())
                .userName(log.getUser().getName())
                .activityType(log.getActivityType())
                .activitySummary(log.getActivitySummary())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
