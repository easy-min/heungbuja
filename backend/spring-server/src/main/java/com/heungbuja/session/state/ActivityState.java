package com.heungbuja.session.state;

import com.heungbuja.session.enums.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 사용자의 현재 활동 상태
 * Redis에 저장됨
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityState implements Serializable {

    /** 활동 타입 */
    private ActivityType type;

    /** 세션 ID (게임 sessionId, 음악 queueId 등) */
    private String sessionId;

    /** 상태 (진행 중, 일시정지 등) */
    private String status;

    /** 마지막 업데이트 시간 */
    private LocalDateTime lastUpdate;

    /** 인터럽트 가능 여부 */
    private boolean canInterrupt;

    /**
     * IDLE 상태 생성
     */
    public static ActivityState idle() {
        return ActivityState.builder()
                .type(ActivityType.IDLE)
                .sessionId(null)
                .status("IDLE")
                .lastUpdate(LocalDateTime.now())
                .canInterrupt(true)
                .build();
    }

    /**
     * 게임 활동 상태 생성
     */
    public static ActivityState game(String sessionId) {
        return ActivityState.builder()
                .type(ActivityType.GAME)
                .sessionId(sessionId)
                .status("IN_PROGRESS")
                .lastUpdate(LocalDateTime.now())
                .canInterrupt(true)
                .build();
    }

    /**
     * 게임 튜토리얼 활동 상태 생성
     */
    public static ActivityState gameTutorial(String sessionId) {
        return ActivityState.builder()
                .type(ActivityType.GAME)
                .sessionId(sessionId)
                .status("TUTORIAL_READY")
                .lastUpdate(LocalDateTime.now())
                .canInterrupt(true)
                .build();
    }

    /**
     * 음악 활동 상태 생성
     */
    public static ActivityState music(String sessionId) {
        return ActivityState.builder()
                .type(ActivityType.MUSIC)
                .sessionId(sessionId)
                .status("PLAYING")
                .lastUpdate(LocalDateTime.now())
                .canInterrupt(true)
                .build();
    }

    /**
     * 응급 상태 생성
     */
    public static ActivityState emergency(Long reportId) {
        return ActivityState.builder()
                .type(ActivityType.EMERGENCY)
                .sessionId(String.valueOf(reportId))
                .status("PENDING")
                .lastUpdate(LocalDateTime.now())
                .canInterrupt(false)  // 응급은 인터럽트 불가
                .build();
    }
}
