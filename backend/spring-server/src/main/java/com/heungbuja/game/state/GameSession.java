package com.heungbuja.game.state;

import com.heungbuja.game.dto.ActionTimelineEvent;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * 실시간 게임 한 판(Session)의 진행 상태를 저장하는 클래스 (Redis 저장용)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSession implements Serializable {

    private String sessionId;
    private Long userId;
    private Long songId;

    /** 1절의 각 동작별 판정 결과(1, 2, 3)를 저장하는 리스트 */
    private List<Integer> verse1Judgments;

    /** 2절의 각 동작별 판정 결과(1, 2, 3)를 저장하는 리스트 */
    private List<Integer> verse2Judgments;

    /** 1절 종료 후 결정된 2절의 안무 레벨 */
    private Integer nextLevel;

    /** 현재 판정해야 할 다음 동작의 인덱스 (actionTimeline의 인덱스) */
    private int nextActionIndex;

    /** 현재 판정 중인 동작의 프레임들을 임시로 모아두는 버퍼 */
    private Map<Double, String> frameBuffer;

    /** 마지막으로 프레임을 수신한 시간 (epoch milliseconds) */
    private long lastFrameReceivedTime;

    /**
     * 현재 이 세션이 종료 처리(레벨 결정, 결과 저장 등) 중인지 여부를 나타내는 플래그
     * transient: Redis에 저장할 필요 없는, 메모리에서만 사용하는 상태값
     */
    @Builder.Default
    private boolean processing = false;


    /**
     * GameSession 객체를 처음 생성할 때 사용하는 정적 팩토리 메소드
     */
    public static GameSession initial(String sessionId, Long userId, Long songId) {
        return GameSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .songId(songId)
                .verse1Judgments(new ArrayList<>())
                .verse2Judgments(new ArrayList<>())
                .nextActionIndex(0)
                .frameBuffer(new HashMap<>())
                .lastFrameReceivedTime(0L)
                .build();
    }
}