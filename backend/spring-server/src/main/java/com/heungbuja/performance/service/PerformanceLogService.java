package com.heungbuja.performance.service;

import com.heungbuja.performance.dto.PerformanceContext;
import com.heungbuja.performance.dto.PerformanceSummary;
import com.heungbuja.performance.entity.PerformanceLog;
import com.heungbuja.performance.repository.PerformanceLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PerformanceLogService {

    private final PerformanceLogRepository performanceLogRepository;

    /**
     * 성능 측정 컨텍스트를 DB에 비동기로 저장
     */
    @Async
    @Transactional
    public void saveContextAsync(PerformanceContext context) {
        try {
            List<PerformanceLog> logs = new ArrayList<>();

            for (PerformanceContext.MeasurementRecord record : context.getRecords()) {
                PerformanceLog log = PerformanceLog.builder()
                    .component(record.getComponent())
                    .methodName(record.getMethodName())
                    .executionTimeMs(record.getExecutionTimeMs())
                    .requestId(context.getRequestId())
                    .userId(context.getUserId())
                    .success(record.getSuccess())
                    .errorMessage(record.getErrorMessage())
                    .build();

                logs.add(log);
            }

            performanceLogRepository.saveAll(logs);
            log.debug("✅ 성능 로그 저장 완료: requestId={}, records={}", context.getRequestId(), logs.size());

        } catch (Exception e) {
            log.error("❌ 성능 로그 저장 실패: requestId={}", context.getRequestId(), e);
        }
    }

    /**
     * 성능 통계 조회
     *
     * @param days 최근 N일
     * @return 성능 요약
     */
    @Transactional(readOnly = true)
    public PerformanceSummary getStatistics(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        // 총 요청 수
        Long totalRequests = performanceLogRepository.countDistinctRequestsSince(startTime);

        // 컴포넌트별 통계
        List<Object[]> statsData = performanceLogRepository.getComponentStats(startTime);
        Map<String, PerformanceSummary.ComponentStats> componentStatsMap = new HashMap<>();

        for (Object[] row : statsData) {
            String component = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            Double avgMs = ((Number) row[2]).doubleValue();
            Long minMs = ((Number) row[3]).longValue();
            Long maxMs = ((Number) row[4]).longValue();
            Double successRate = ((Number) row[5]).doubleValue();

            componentStatsMap.put(component, PerformanceSummary.ComponentStats.builder()
                .component(component)
                .count(count)
                .avgMs(Math.round(avgMs * 100.0) / 100.0)  // 소수점 2자리
                .minMs(minMs)
                .maxMs(maxMs)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .build());
        }

        // 최근 로그 (상위 20개)
        List<PerformanceLog> recentLogs = performanceLogRepository.findTop100ByOrderByCreatedAtDesc()
            .stream()
            .limit(20)
            .toList();

        List<PerformanceSummary.RecentLog> recentLogDtos = recentLogs.stream()
            .map(log -> PerformanceSummary.RecentLog.builder()
                .component(log.getComponent())
                .methodName(log.getMethodName())
                .executionTimeMs(log.getExecutionTimeMs())
                .success(log.getSuccess())
                .createdAt(log.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build())
            .toList();

        return PerformanceSummary.builder()
            .period("최근 " + days + "일")
            .totalRequests(totalRequests)
            .componentStats(componentStatsMap)
            .recentLogs(recentLogDtos)
            .build();
    }

    /**
     * 특정 컴포넌트의 로그 조회
     */
    @Transactional(readOnly = true)
    public List<PerformanceLog> getLogsByComponent(String component) {
        return performanceLogRepository.findByComponentOrderByCreatedAtDesc(component);
    }
}
