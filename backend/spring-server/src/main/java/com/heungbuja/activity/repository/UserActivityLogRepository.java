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
     * User를 함께 조회하여 LazyInitializationException 방지
     */
    @Query("SELECT l FROM UserActivityLog l JOIN FETCH l.user ORDER BY l.createdAt DESC")
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 특정 사용자의 활동 로그 조회 (페이징)
     */
    @Query("SELECT l FROM UserActivityLog l JOIN FETCH l.user WHERE l.user.id = :userId ORDER BY l.createdAt DESC")
    Page<UserActivityLog> findByUser_IdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    /**
     * 활동 타입별 필터링 조회 (페이징)
     */
    @Query("SELECT l FROM UserActivityLog l JOIN FETCH l.user WHERE l.activityType = :activityType ORDER BY l.createdAt DESC")
    Page<UserActivityLog> findByActivityTypeOrderByCreatedAtDesc(@Param("activityType") ActivityType activityType, Pageable pageable);

    /**
     * 특정 사용자 + 활동 타입 필터링 조회 (페이징)
     */
    @Query("SELECT l FROM UserActivityLog l JOIN FETCH l.user WHERE l.user.id = :userId AND l.activityType = :activityType ORDER BY l.createdAt DESC")
    Page<UserActivityLog> findByUser_IdAndActivityTypeOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("activityType") ActivityType activityType,
            Pageable pageable
    );

    /**
     * 기간별 필터링 조회 (페이징)
     */
    @Query("SELECT l FROM UserActivityLog l JOIN FETCH l.user WHERE l.createdAt >= :startDate AND l.createdAt < :endDate ORDER BY l.createdAt DESC")
    Page<UserActivityLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * 특정 사용자 + 기간별 필터링 조회 (페이징)
     */
    @Query("SELECT l FROM UserActivityLog l JOIN FETCH l.user WHERE l.user.id = :userId AND l.createdAt >= :startDate AND l.createdAt < :endDate ORDER BY l.createdAt DESC")
    Page<UserActivityLog> findByUser_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
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
