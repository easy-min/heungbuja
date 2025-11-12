package com.heungbuja.game.state;

import com.heungbuja.game.dto.ActionTimelineEvent;
import com.heungbuja.game.dto.SectionInfo;
import com.heungbuja.song.domain.SongLyrics;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.heungbuja.game.dto.ActionTimelineEvent;

@Getter
@Setter
@Builder
public class GameState implements Serializable {
    // 기본 정보
    private String sessionId;
    private Long userId;
    private Long songId;

    /**
     * 게임 전체의 동작 타임라인 (song 서비스에서 생성하여 Redis에 저장)
     */
    private List<ActionTimelineEvent> actionTimeline;

    /**
     * 현재 판정해야 할 다음 동작의 인덱스 (actionTimeline의 인덱스)
     * 기본값: 0
     */
    private int nextActionIndex;

    /**
     * 현재 판정 중인 동작(nextActionIndex에 해당하는)의 프레임들을 임시로 모아두는 버퍼
     * Key: 프레임의 재생 시간(초), Value: 프레임 데이터(Base64)
     */
    private Map<Double, String> frameBuffer;

    /** 1절의 각 묶음(16박스)별 채점 결과를 저장하는 리스트 */
    private List<Integer> verse1Judgments;

    /** 2절의 각 묶음별 채점 결과를 저장하는 리스트 */
    private List<Integer> verse2Judgments;

    /** 1절 종료 후 결정된 2절의 안무 레벨 */
    private Integer nextLevel;

    /** 튜토리얼 성공 횟수 */
    private Integer tutorialSuccessCount;

    /**
     * 튜토리얼 성공 횟수 증가
     */
    public void incrementTutorialSuccess() {
        if (this.tutorialSuccessCount == null) {
            this.tutorialSuccessCount = 0;
        }
        this.tutorialSuccessCount++;
    }

    /**
     * GameState가 처음 생성될 때 호출할 수 있는 정적 팩토리 메소드
     * (기존 코드 호환용 - 사용 안 함)
     */
    public static GameState initial(String sessionId, Long userId, Long songId) {
        return GameState.builder()
                .sessionId(sessionId)
                .userId(userId)
                .songId(songId)
                .actionTimeline(new ArrayList<>())
                .nextActionIndex(0)
                .frameBuffer(new HashMap<>())
                .verse1Judgments(new ArrayList<>()) // 빈 리스트로 초기화
                .verse2Judgments(new ArrayList<>()) // 빈 리스트로 초기화
//                .batchCompletedCount(0) // 0으로 초기화
                .build();
    }
}