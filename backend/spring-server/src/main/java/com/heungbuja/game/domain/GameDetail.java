package com.heungbuja.game.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * MongoDB의 'game_details' 컬렉션과 매핑되는 Document 클래스
 * 게임의 상세 채점 데이터를 담고 있음
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "game_details")
public class GameDetail {

    @Id
    private String id; // MongoDB의 고유 ID

    /** 게임 세션 ID (MySQL GameResult의 sessionId와 매핑) */
    private String sessionId;

    /** 1절 동작 데이터 */
    private List<Movement> verse1Movements;

    /** 2절 동작 데이터 */
    private List<Movement> verse2Movements;

    /** 1절 통계 */
    private Statistics verse1Stats;

    /** 2절 통계 */
    private Statistics verse2Stats;

    /**
     * 개별 동작 데이터
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Movement {
        /** 타임스탬프 (밀리초) */
        private long timestamp;

        /** 동작 코드 */
        private String action;

        /** 점수 (1-3: BAD, GOOD, PERFECT) */
        private int score;

        /** 정확도 (0-100) */
        private int accuracy;

        /** 정답 여부 */
        private boolean correct;
    }

    /**
     * 통계 데이터
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics {
        /** 총 동작 수 */
        private int totalMovements;

        /** 정답 동작 수 */
        private int correctMovements;

        /** 평균 점수 */
        private double averageScore;

        /** PERFECT 수 */
        private int perfectCount;

        /** GOOD 수 */
        private int goodCount;

        /** BAD 수 */
        private int badCount;
    }
}
