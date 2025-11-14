package com.heungbuja.performance.controller;

import com.heungbuja.performance.dto.PerformanceSummary;
import com.heungbuja.performance.entity.PerformanceLog;
import com.heungbuja.performance.service.PerformanceLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 성능 측정 결과 조회 API (Admin용)
 */
@RestController
@RequestMapping("/api/admin/performance")
@RequiredArgsConstructor
@Slf4j
public class PerformanceController {

    private final PerformanceLogService performanceLogService;

    /**
     * 성능 통계 조회
     *
     * GET /api/admin/performance/stats?days=7
     */
    @GetMapping("/stats")
    public ResponseEntity<PerformanceSummary> getStatistics(
        @RequestParam(defaultValue = "7") int days
    ) {
        log.info("📊 성능 통계 조회: 최근 {}일", days);

        PerformanceSummary summary = performanceLogService.getStatistics(days);

        return ResponseEntity.ok(summary);
    }

    /**
     * 특정 컴포넌트의 로그 조회
     *
     * GET /api/admin/performance/logs/STT
     */
    @GetMapping("/logs/{component}")
    public ResponseEntity<List<PerformanceLog>> getLogsByComponent(
        @PathVariable String component
    ) {
        log.info("📋 컴포넌트별 로그 조회: component={}", component);

        List<PerformanceLog> logs = performanceLogService.getLogsByComponent(component);

        return ResponseEntity.ok(logs);
    }
}
