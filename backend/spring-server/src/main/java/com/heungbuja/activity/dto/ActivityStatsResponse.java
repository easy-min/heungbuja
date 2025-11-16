package com.heungbuja.activity.dto;

import com.heungbuja.activity.enums.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 활동 통계 응답 DTO (관리자 페이지용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatsResponse {

    /**
     * 활동 타입별 카운트
     * 예: {"MUSIC_PLAY": 120, "GAME_START": 45, "EMERGENCY": 3}
     */
    private Map<ActivityType, Long> stats;

    /**
     * 전체 활동 수
     */
    private Long totalCount;

    /**
     * 통계 생성
     */
    public static ActivityStatsResponse from(Map<ActivityType, Long> stats) {
        long total = stats.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        return ActivityStatsResponse.builder()
                .stats(stats)
                .totalCount(total)
                .build();
    }
}
