package com.heungbuja.activity.service;

import com.heungbuja.activity.entity.UserActivityLog;
import com.heungbuja.activity.enums.ActivityType;
import com.heungbuja.user.entity.User;
import com.heungbuja.voice.enums.Intent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 사용자 활동 로그 서비스
 */
public interface ActivityLogService {

    /**
     * 활동 로그 저장 (Intent 기반)
     *
     * @param user 사용자
     * @param intent 음성 명령 의도
     */
    void saveActivityLog(User user, Intent intent);

    /**
     * 전체 활동 로그 조회 (페이징)
     */
    Page<UserActivityLog> findAllLogs(Pageable pageable);

    /**
     * 특정 사용자의 활동 로그 조회 (페이징)
     */
    Page<UserActivityLog> findLogsByUserId(Long userId, Pageable pageable);

    /**
     * 활동 타입별 필터링 조회 (페이징)
     */
    Page<UserActivityLog> findLogsByActivityType(ActivityType activityType, Pageable pageable);

    /**
     * 특정 사용자 + 활동 타입 필터링 조회 (페이징)
     */
    Page<UserActivityLog> findLogsByUserIdAndActivityType(
            Long userId,
            ActivityType activityType,
            Pageable pageable
    );

    /**
     * 기간별 필터링 조회 (페이징)
     */
    Page<UserActivityLog> findLogsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * 특정 사용자 + 기간별 필터링 조회 (페이징)
     */
    Page<UserActivityLog> findLogsByUserIdAndDateRange(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * 활동 타입별 통계 (일별)
     */
    Map<ActivityType, Long> getStatsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 특정 사용자의 활동 타입별 통계 (일별)
     */
    Map<ActivityType, Long> getStatsByUserAndDateRange(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}
