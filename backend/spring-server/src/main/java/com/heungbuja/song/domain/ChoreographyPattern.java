package com.heungbuja.song.domain;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * MongoDB의 'choreography_patterns' 컬렉션과 매핑되는 Document 클래스.
 * 각 노래별로 사용되는 동작 패턴(시퀀스)의 정의를 담고 있습니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "choreography_patterns")
public class ChoreographyPattern {

    @Id
    private String id; // MongoDB 고유 ID

    /**
     * 이 안무 패턴들이 속한 노래의 고유 ID (MySQL의 Song.id)
     */
    private Long songId;

    /**
     * 해당 노래에서 사용되는 모든 동작 패턴들의 리스트
     */
    private List<Pattern> patterns;

    /**
     * 단일 동작 패턴(예: 'A', 'B1')을 정의하는 내부 클래스
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pattern {
        /**
         * 패턴을 식별하는 고유 ID (예: "A", "B1", "B2")
         */
        private String patternId;

        /**
         * 패턴에 대한 설명 (예: "1절 기본 16비트 반복 패턴")
         */
        private String description;

        /**
         * 16비트 동안 수행할 동작 코드의 순차적 나열.
         * 0은 '쉬는' 동작을 의미합니다.
         */
        private List<Integer> sequence;
    }
}