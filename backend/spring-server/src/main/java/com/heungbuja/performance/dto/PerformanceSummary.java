package com.heungbuja.performance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 성능 측정 요약 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceSummary {

    /**
     * 측정 기간
     */
    private String period;

    /**
     * 총 요청 수
     */
    private Long totalRequests;

    /**
     * 컴포넌트별 평균 실행시간 (ms)
     */
    private Map<String, ComponentStats> componentStats;

    /**
     * 최근 측정 기록 (상위 N개)
     */
    private List<RecentLog> recentLogs;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComponentStats {
        private String component;
        private Long count;           // 측정 횟수
        private Double avgMs;         // 평균 (ms)
        private Long minMs;           // 최소 (ms)
        private Long maxMs;           // 최대 (ms)
        private Double successRate;   // 성공률 (%)
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentLog {
        private String component;
        private String methodName;
        private Long executionTimeMs;
        private Boolean success;
        private String createdAt;
    }
}
