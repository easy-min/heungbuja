package com.heungbuja.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자별 게임 통계 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGameStatsResponse {

    /** 사용자 ID */
    private Long userId;

    /** 사용자 이름 */
    private String userName;

    /** 총 게임 횟수 */
    private Long totalGames;

    /** 완료된 게임 횟수 */
    private Long completedGames;

    /** 평균 점수 (1절) */
    private Double averageVerse1Score;

    /** 평균 점수 (2절) */
    private Double averageVerse2Score;

    /** 전체 평균 점수 */
    private Double overallAverageScore;

    /** PERFECT 비율 (%) */
    private Double perfectRate;

    /** GOOD 비율 (%) */
    private Double goodRate;

    /** BAD 비율 (%) */
    private Double badRate;

    /** 최근 게임 기록 (최대 5개) */
    private List<RecentGameInfo> recentGames;

    /** 마지막 게임 일시 */
    private LocalDateTime lastPlayedAt;

    /**
     * 최근 게임 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentGameInfo {
        /** 게임 세션 ID */
        private String sessionId;

        /** 노래 제목 */
        private String songTitle;

        /** 점수 (1절) */
        private Double verse1Score;

        /** 점수 (2절) */
        private Double verse2Score;

        /** 게임 상태 */
        private String status;

        /** 플레이 시간 */
        private LocalDateTime playedAt;
    }
}
