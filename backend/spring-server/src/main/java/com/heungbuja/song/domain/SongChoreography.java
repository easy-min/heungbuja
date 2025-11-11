package com.heungbuja.song.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

/**
 * MongoDB의 'song_choreographies' 컬렉션과 매핑되는 Document 클래스.
 * 각 노래의 절(verse)마다 어떤 동작 패턴(pattern)을 사용할지를 정의하는 '지시서' 역할을 합니다.
 */
@Getter
@Setter
@Document(collection = "song_choreographies")
public class SongChoreography {

    @Id
    private String id;

    /**
     * 이 안무 지시서가 속한 노래의 고유 ID (MySQL의 Song.id)
     */
    private Long songId;

    /**
     * 해당 노래에서 사용 가능한 안무 버전들의 리스트 (보통 하나만 존재)
     */
    private List<Version> versions;

    /**
     * 단일 안무 버전을 정의하는 내부 클래스 (예: "default_v1")
     */
    @Getter
    @Setter
    public static class Version {
        /**
         * 버전을 식별하는 ID (예: "default_v1")
         */
        private String id;

        /**
         * 1절에서 사용할 패턴 정보
         */
        private VersePatternInfo verse1;

        /**
         * 2절에서 사용할 패턴 정보 (레벨별로 나뉨)
         */
        private List<VerseLevelPatternInfo> verse2;
    }

    /**
     * 1절과 같이 레벨 구분이 없는 절의 패턴 정보를 담는 내부 클래스
     */
    @Getter
    @Setter
    public static class VersePatternInfo {
        /**
         * 사용할 패턴의 ID (ChoreographyPattern의 Pattern.id 참조)
         */
        private String patternId;

        /**
         * 해당 패턴을 몇 번 반복할지
         */
        private int repeat;
    }

    /**
     * 2절과 같이 레벨 구분이 있는 절의 패턴 정보를 담는 내부 클래스
     */
    @Getter
    @Setter
    public static class VerseLevelPatternInfo {
        /**
         * 난이도 레벨 (1, 2, 3)
         */
        private int level;

        /**
         * 사용할 패턴의 ID (ChoreographyPattern의 Pattern.id 참조)
         */
        private String patternId;

        /**
         * 해당 패턴을 몇 번 반복할지
         */
        private int repeat;
    }
}