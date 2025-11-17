package com.heungbuja.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 동작별 수행도 분석 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionPerformanceResponse {

    /** 사용자 ID */
    private Long userId;

    /** 사용자 이름 */
    private String userName;

    /** 동작별 성과 리스트 */
    private List<ActionScore> actionScores;

    /** 가장 잘하는 동작 TOP 3 */
    private List<ActionScore> topActions;

    /** 개선이 필요한 동작 (점수 낮은 순) */
    private List<ActionScore> weakActions;

    /**
     * 동작별 점수 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionScore {
        /** 동작 코드 */
        private Integer actionCode;

        /** 동작 이름 */
        private String actionName;

        /** 동작 설명 */
        private String actionDescription;

        /** 평균 점수 */
        private Double averageScore;

        /** 시도 횟수 */
        private Long attemptCount;

        /** 성공률 (정답률, %) */
        private Double successRate;
    }
}
