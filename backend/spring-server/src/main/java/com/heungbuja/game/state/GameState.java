package com.heungbuja.game.state;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
public class GameState implements Serializable {
    private String sessionId;
    // 이 userId는 게임 세션 내내 어떤 사용자의 게임인지를 기억하는 역할을 합니다.
    // 게임 종료 후, 이 userId를 사용해 User와 GameResult를 연결합니다.
    private Long userId;
    private Long songId;

    /** 1절의 각 묶음(16박스)별 채점 결과를 저장하는 리스트 */
    private List<Integer> verse1Judgments;

    /** 2절의 각 묶음별 채점 결과를 저장하는 리스트 */
    private List<Integer> verse2Judgments;

    /** AI 서버에서 분석이 완료된 총 묶음의 개수 (최대 12개) */
//    private int batchCompletedCount;

    /** 1절 종료 후 결정된 2절의 안무 레벨 */
    private Integer nextLevel;

    /**
     * GameState가 처음 생성될 때 호출할 수 있는 정적 팩토리 메소드
     */
    public static GameState initial(String sessionId, Long userId, Long songId) {
        return GameState.builder()
                .sessionId(sessionId)
                .userId(userId)
                .songId(songId)
                .verse1Judgments(new ArrayList<>()) // 빈 리스트로 초기화
                .verse2Judgments(new ArrayList<>()) // 빈 리스트로 초기화
//                .batchCompletedCount(0) // 0으로 초기화
                .build();
    }
}