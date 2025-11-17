package com.heungbuja.activity.repository;

import com.heungbuja.activity.entity.UserActivityLog;
import com.heungbuja.activity.enums.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 활동 로그 Repository
 */
@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    /**
     * 전체 활동 로그 조회 (페이징)
     */
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 특정 사용자의 활동 로그 조회 (페이징)
     */
    Page<UserActivityLog> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 활동 타입별 필터링 조회 (페이징)
     */
    Page<UserActivityLog> findByActivityTypeOrderByCreatedAtDesc(ActivityType activityType, Pageable pageable);

    /**
     * 특정 사용자 + 활동 타입 필터링 조회 (페이징)
     */
    Page<UserActivityLog> findByUser_IdAndActivityTypeOrderByCreatedAtDesc(
            Long userId,
            ActivityType activityType,
            Pageable pageable
    );

    /**
     * 기간별 필터링 조회 (페이징)
     */
    Page<UserActivityLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * 특정 사용자 + 기간별 필터링 조회 (페이징)
     */
    Page<UserActivityLog> findByUser_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * 활동 타입별 통계 (일별)
     */
    @Query("SELECT l.activityType, COUNT(l) " +
            "FROM UserActivityLog l " +
            "WHERE l.createdAt >= :startDate AND l.createdAt < :endDate " +
            "GROUP BY l.activityType")
    List<Object[]> countByActivityTypeAndDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 특정 사용자의 활동 타입별 통계 (일별)
     */
    @Query("SELECT l.activityType, COUNT(l) " +
            "FROM UserActivityLog l " +
            "WHERE l.user.id = :userId " +
            "AND l.createdAt >= :startDate AND l.createdAt < :endDate " +
            "GROUP BY l.activityType")
    List<Object[]> countByUserAndActivityTypeAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
