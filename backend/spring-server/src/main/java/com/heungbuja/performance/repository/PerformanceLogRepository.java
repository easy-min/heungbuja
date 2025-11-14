package com.heungbuja.performance.repository;

import com.heungbuja.performance.entity.PerformanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PerformanceLogRepository extends JpaRepository<PerformanceLog, Long> {

    /**
     * 특정 기간 내 로그 조회
     */
    List<PerformanceLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime startTime,
        LocalDateTime endTime
    );

    /**
     * 특정 컴포넌트의 로그 조회
     */
    List<PerformanceLog> findByComponentOrderByCreatedAtDesc(String component);

    /**
     * 최근 N개 로그 조회
     */
    List<PerformanceLog> findTop100ByOrderByCreatedAtDesc();

    /**
     * 컴포넌트별 통계 (평균, 최소, 최대, 개수)
     */
    @Query("""
        SELECT p.component as component,
               COUNT(p) as count,
               AVG(p.executionTimeMs) as avgMs,
               MIN(p.executionTimeMs) as minMs,
               MAX(p.executionTimeMs) as maxMs,
               SUM(CASE WHEN p.success = true THEN 1 ELSE 0 END) * 100.0 / COUNT(p) as successRate
        FROM PerformanceLog p
        WHERE p.createdAt >= :startTime
        GROUP BY p.component
        ORDER BY avgMs DESC
        """)
    List<Object[]> getComponentStats(@Param("startTime") LocalDateTime startTime);

    /**
     * 전체 요청 수 (특정 기간)
     */
    @Query("SELECT COUNT(DISTINCT p.requestId) FROM PerformanceLog p WHERE p.createdAt >= :startTime")
    Long countDistinctRequestsSince(@Param("startTime") LocalDateTime startTime);
}
