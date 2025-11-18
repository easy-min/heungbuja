package com.heungbuja.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 시간대별 활동 추이 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTrendResponse {

    /** 사용자 ID */
    private Long userId;

    /** 사용자 이름 */
    private String userName;

    /** 조회 기간 (일) */
    private Integer periodDays;

    /** 일별 활동 데이터 */
    private List<DailyActivity> dailyActivities;

    /** 총 게임 횟수 (기간 내) */
    private Long totalGames;

    /** 평균 일일 게임 횟수 */
    private Double averageDailyGames;

    /** 활동량 추세 (INCREASING, STABLE, DECREASING) */
    private String trend;

    /** 가장 활발한 요일 */
    private String mostActiveDayOfWeek;

    /**
     * 일별 활동 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyActivity {
        /** 날짜 */
        private LocalDate date;

        /** 요일 */
        private String dayOfWeek;

        /** 게임 횟수 */
        private Long gameCount;

        /** 평균 점수 */
        private Double averageScore;

        /** 총 플레이 시간 (분) */
        private Long totalPlayTimeMinutes;
    }
}
