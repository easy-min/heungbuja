package com.heungbuja.session.service;

import com.heungbuja.session.enums.ActivityType;
import com.heungbuja.session.state.ActivityState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 세션 상태 관리 서비스
 * Redis를 사용하여 사용자의 현재 활동 상태를 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStateService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** Redis 키 접두사 */
    private static final String USER_ACTIVITY_PREFIX = "user:activity:";
    private static final String SESSION_STATUS_PREFIX = "session:status:";
    private static final String INTERRUPT_LOCK_PREFIX = "session:interrupt:lock:";

    /** 기본 TTL */
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    /**
     * 사용자의 현재 활동 상태 조회
     */
    public ActivityState getCurrentActivity(Long userId) {
        String key = USER_ACTIVITY_PREFIX + userId;

        try {
            ActivityState state = (ActivityState) redisTemplate.opsForValue().get(key);

            if (state == null) {
                log.debug("활동 상태 없음, IDLE 반환: userId={}", userId);
                return ActivityState.idle();
            }

            return state;

        } catch (Exception e) {
            // 역직렬화 실패 시 (타입 불일치, 형식 변경 등)
            log.warn("활동 상태 역직렬화 실패, 키 삭제 후 IDLE 반환: userId={}, error={}",
                    userId, e.getMessage());
            redisTemplate.delete(key);  // 손상된 데이터 삭제
            return ActivityState.idle();
        }
    }

    /**
     * 사용자의 활동 상태 설정
     */
    public void setCurrentActivity(Long userId, ActivityState state) {
        String key = USER_ACTIVITY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, state, DEFAULT_TTL);
        log.info("활동 상태 설정: userId={}, type={}, sessionId={}",
                userId, state.getType(), state.getSessionId());
    }

    /**
     * 사용자의 활동 상태 삭제 (IDLE로 전환)
     */
    public void clearActivity(Long userId) {
        String key = USER_ACTIVITY_PREFIX + userId;
        redisTemplate.delete(key);
        log.info("활동 상태 삭제: userId={}", userId);
    }

    /**
     * 세션 상태 설정 (게임, 음악 등)
     */
    public void setSessionStatus(String sessionId, String status) {
        String key = SESSION_STATUS_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, status, DEFAULT_TTL);
        log.debug("세션 상태 설정: sessionId={}, status={}", sessionId, status);
    }

    /**
     * 세션 상태 조회
     */
    public String getSessionStatus(String sessionId) {
        String key = SESSION_STATUS_PREFIX + sessionId;
        return (String) redisTemplate.opsForValue().get(key);
    }

    /**
     * 세션 상태 삭제
     */
    public void clearSessionStatus(String sessionId) {
        String key = SESSION_STATUS_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.debug("세션 상태 삭제: sessionId={}", sessionId);
    }

    /**
     * 인터럽트 플래그 설정 (원자적 락 획득)
     * @return true: 락 획득 성공, false: 이미 다른 인터럽트 진행 중
     */
    public boolean trySetInterrupt(String sessionId, String reason) {
        String lockKey = INTERRUPT_LOCK_PREFIX + sessionId;

        // SETNX: Set if Not eXists (원자적 연산)
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, reason, 5, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquired)) {
            // 락 획득 성공 → 세션 상태를 INTERRUPTING으로 변경
            setSessionStatus(sessionId, "INTERRUPTING");
            log.info("인터럽트 락 획득: sessionId={}, reason={}", sessionId, reason);
            return true;
        } else {
            log.warn("인터럽트 락 획득 실패 (이미 처리 중): sessionId={}", sessionId);
            return false;
        }
    }

    /**
     * 인터럽트 락 해제
     */
    public void releaseInterruptLock(String sessionId) {
        String lockKey = INTERRUPT_LOCK_PREFIX + sessionId;
        redisTemplate.delete(lockKey);
        log.debug("인터럽트 락 해제: sessionId={}", sessionId);
    }

    /**
     * 활동이 인터럽트 가능한지 확인
     */
    public boolean canInterrupt(Long userId) {
        ActivityState state = getCurrentActivity(userId);
        return state.isCanInterrupt();
    }

    /**
     * 게임 진행 중인지 확인
     */
    public boolean isGameInProgress(Long userId) {
        ActivityState state = getCurrentActivity(userId);
        return state.getType() == ActivityType.GAME &&
               "IN_PROGRESS".equals(state.getStatus());
    }

    /**
     * 음악 재생 중인지 확인
     */
    public boolean isMusicPlaying(Long userId) {
        ActivityState state = getCurrentActivity(userId);
        return state.getType() == ActivityType.MUSIC &&
               "PLAYING".equals(state.getStatus());
    }

    /**
     * 응급 상황인지 확인
     */
    public boolean isEmergency(Long userId) {
        ActivityState state = getCurrentActivity(userId);
        return state.getType() == ActivityType.EMERGENCY;
    }
}
