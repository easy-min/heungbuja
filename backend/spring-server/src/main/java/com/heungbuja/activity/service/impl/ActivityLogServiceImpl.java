package com.heungbuja.activity.service.impl;

import com.heungbuja.activity.entity.UserActivityLog;
import com.heungbuja.activity.enums.ActivityType;
import com.heungbuja.activity.repository.UserActivityLogRepository;
import com.heungbuja.activity.service.ActivityLogService;
import com.heungbuja.user.entity.User;
import com.heungbuja.voice.enums.Intent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 사용자 활동 로그 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityLogServiceImpl implements ActivityLogService {

    private final UserActivityLogRepository activityLogRepository;

    /**
     * Intent -> ActivityType 및 요약 메시지 매핑
     */
    private static class ActivityMapping {
        final ActivityType type;
        final String summary;

        ActivityMapping(ActivityType type, String summary) {
            this.type = type;
            this.summary = summary;
        }
    }

    private static final Map<Intent, ActivityMapping> INTENT_ACTIVITY_MAP = new HashMap<>();

    static {
        // 음악 재생
        INTENT_ACTIVITY_MAP.put(Intent.SELECT_BY_ARTIST,
                new ActivityMapping(ActivityType.MUSIC_PLAY, "음악을 재생했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.SELECT_BY_TITLE,
                new ActivityMapping(ActivityType.MUSIC_PLAY, "음악을 재생했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.SELECT_BY_ARTIST_TITLE,
                new ActivityMapping(ActivityType.MUSIC_PLAY, "음악을 재생했습니다"));

        // 음악 제어
        INTENT_ACTIVITY_MAP.put(Intent.MUSIC_PAUSE,
                new ActivityMapping(ActivityType.MUSIC_CONTROL, "음악을 일시정지했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.MUSIC_RESUME,
                new ActivityMapping(ActivityType.MUSIC_CONTROL, "음악을 재개했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.MUSIC_NEXT,
                new ActivityMapping(ActivityType.MUSIC_CONTROL, "다음 곡으로 이동했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.MUSIC_STOP,
                new ActivityMapping(ActivityType.MUSIC_CONTROL, "음악을 종료했습니다"));

        // 연속 재생
        INTENT_ACTIVITY_MAP.put(Intent.PLAY_NEXT_IN_QUEUE,
                new ActivityMapping(ActivityType.MUSIC_PLAY, "대기열 다음 곡을 재생했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.PLAY_MORE_LIKE_THIS,
                new ActivityMapping(ActivityType.MUSIC_PLAY, "비슷한 노래를 재생했습니다"));

        // 모드 변경
        INTENT_ACTIVITY_MAP.put(Intent.MODE_HOME,
                new ActivityMapping(ActivityType.MODE_CHANGE, "홈 화면으로 이동했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.MODE_LISTENING,
                new ActivityMapping(ActivityType.MODE_CHANGE, "감상 모드로 변경했습니다"));

        // 게임
        INTENT_ACTIVITY_MAP.put(Intent.MODE_EXERCISE,
                new ActivityMapping(ActivityType.GAME_START, "게임을 시작했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.MODE_EXERCISE_END,
                new ActivityMapping(ActivityType.GAME_END, "게임을 종료했습니다"));

        // 응급
        INTENT_ACTIVITY_MAP.put(Intent.EMERGENCY,
                new ActivityMapping(ActivityType.EMERGENCY, "응급 상황을 신고했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.EMERGENCY_CANCEL,
                new ActivityMapping(ActivityType.EMERGENCY, "응급 신고를 취소했습니다"));
        INTENT_ACTIVITY_MAP.put(Intent.EMERGENCY_CONFIRM,
                new ActivityMapping(ActivityType.EMERGENCY, "응급 상황을 즉시 확정했습니다"));

        // 인식 불가
        INTENT_ACTIVITY_MAP.put(Intent.UNKNOWN,
                new ActivityMapping(ActivityType.UNKNOWN, "명령을 인식하지 못했습니다"));
    }

    @Override
    @Transactional
    public void saveActivityLog(User user, Intent intent) {
        ActivityMapping mapping = INTENT_ACTIVITY_MAP.get(intent);

        if (mapping == null) {
            log.warn("Intent에 대한 ActivityMapping이 없습니다: {}", intent);
            mapping = new ActivityMapping(ActivityType.UNKNOWN, "알 수 없는 활동");
        }

        UserActivityLog activityLog = UserActivityLog.builder()
                .user(user)
                .activityType(mapping.type)
                .activitySummary(mapping.summary)
                .build();

        activityLogRepository.save(activityLog);

        log.debug("활동 로그 저장: userId={}, activityType={}, summary='{}'",
                user.getId(), mapping.type, mapping.summary);
    }

    @Override
    public Page<UserActivityLog> findAllLogs(Pageable pageable) {
        return activityLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Override
    public Page<UserActivityLog> findLogsByUserId(Long userId, Pageable pageable) {
        return activityLogRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public Page<UserActivityLog> findLogsByActivityType(ActivityType activityType, Pageable pageable) {
        return activityLogRepository.findByActivityTypeOrderByCreatedAtDesc(activityType, pageable);
    }

    @Override
    public Page<UserActivityLog> findLogsByUserIdAndActivityType(
            Long userId,
            ActivityType activityType,
            Pageable pageable) {
        return activityLogRepository.findByUser_IdAndActivityTypeOrderByCreatedAtDesc(
                userId, activityType, pageable);
    }

    @Override
    public Page<UserActivityLog> findLogsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        return activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                startDate, endDate, pageable);
    }

    @Override
    public Page<UserActivityLog> findLogsByUserIdAndDateRange(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        return activityLogRepository.findByUser_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                userId, startDate, endDate, pageable);
    }

    @Override
    public Map<ActivityType, Long> getStatsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate) {
        List<Object[]> results = activityLogRepository.countByActivityTypeAndDateRange(
                startDate, endDate);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (ActivityType) row[0],
                        row -> (Long) row[1]
                ));
    }

    @Override
    public Map<ActivityType, Long> getStatsByUserAndDateRange(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        List<Object[]> results = activityLogRepository.countByUserAndActivityTypeAndDateRange(
                userId, startDate, endDate);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (ActivityType) row[0],
                        row -> (Long) row[1]
                ));
    }
}
