package com.heungbuja.game.dto;

import com.heungbuja.song.domain.SongLyrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStartResponse {
    private String sessionId;

    /**
     * 노래 ID
     */
    private Long songId;

    /**
     * 노래 제목
     */
    private String songTitle;

    /**
     * 가수명
     */
    private String songArtist;

    /**
     * 노래 오디오 파일의 S3 URL
     */
    private String audioUrl;

    /**
     * 영상 URL 맵 (intro, verse1, verse2_level1, verse2_level2, verse2_level3)
     */
    private Map<String, String> videoUrls;

    /** 노래의 BPM (Beats Per Minute) */
    private double bpm;

    /** 노래 전체 길이 (초) */
    private double duration;

    /** 노래의 주요 섹션별 시작 시간 정보 (intro, verse1, break, verse2) */
    private Map<String, Double> sectionInfo;

    /** 카메라 세그먼트 정보 (verse1cam, verse2cam) */
    private SegmentInfo segmentInfo;

    /** 가사 정보 (라인 배열) */
    private List<SongLyrics.Line> lyricsInfo;

    /** 1절의 동작 타임라인 */
    private List<ActionTimelineEvent> verse1Timeline;

    /** 2절의 레벨별 동작 타임라인 */
    private Verse2Timeline verse2Timeline;

    /**
     * 각 절(verse)의 카메라 타이밍 정보를 담는 내부 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentRange {
        private double startTime;
        private double endTime;
    }

    /**
     * 세그먼트 정보 (verse1cam, verse2cam)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentInfo {
        private SegmentRange verse1cam;
        private SegmentRange verse2cam;
    }

    /**
     * 2절 레벨별 타임라인
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Verse2Timeline {
        private List<ActionTimelineEvent> level1;
        private List<ActionTimelineEvent> level2;
        private List<ActionTimelineEvent> level3;
    }
}