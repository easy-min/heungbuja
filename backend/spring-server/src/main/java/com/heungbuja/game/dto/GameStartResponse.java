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

    /** 노래의 주요 섹션별 시작 시간 정보 */
    private SectionInfo sectionInfo;

    /** 가사 정보 (원본 JSON 그대로 전달) */
    private SongLyrics lyricsInfo;

    /** 1절의 동작 타임라인 */
    private List<ActionTimelineEvent> verse1Timeline;

    /** 2절의 레벨별 동작 타임라인을 담는 Map */
    private Map<String, List<ActionTimelineEvent>> verse2Timelines;
}