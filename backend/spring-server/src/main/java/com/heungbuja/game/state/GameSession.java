package com.heungbuja.game.state;

import com.heungbuja.game.dto.ActionTimelineEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 실시간 게임 한 판(Session)의 진행 상태를 저장하는 클래스 (Redis 저장용)
 */
@Getter
@Setter
@Builder
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

    /** 게임 전체의 동작 타임라인 (음성 명령 처리부에서 생성하여 Redis에 저장) */
    private List<ActionTimelineEvent> actionTimeline;

    /** 1절, 2절의 종료 시간 (시간으로 절을 구분하기 위함) */
    private double verse1EndTime;
    private double verse2EndTime;

    /** 현재 판정해야 할 다음 동작의 인덱스 (actionTimeline의 인덱스) */
    private int nextActionIndex;

    /** 현재 판정 중인 동작의 프레임들을 임시로 모아두는 버퍼 */
    private Map<Double, String> frameBuffer;

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
                .actionTimeline(new ArrayList<>())
                .nextActionIndex(0)
                .frameBuffer(new HashMap<>())
                .build();
    }
}