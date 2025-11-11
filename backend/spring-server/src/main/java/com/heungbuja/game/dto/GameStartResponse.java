package com.heungbuja.game.dto;

import com.heungbuja.song.domain.SongLyrics;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class GameStartResponse {
    private String sessionId;
//    private String websocketUrl;

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
    private Map<String, String> videoUrls;

    /** 노래의 BPM (Beats Per Minute) */
    private double bpm;

    /** 노래 전체 길이 (초) */
    private double duration;

    /** 노래의 주요 섹션별 시작 시간 정보 */
    private SectionInfo sectionInfo;

    /** 가사 정보 (원본 JSON 그대로 전달) */
    private SongLyrics lyricsInfo;

    /**
     * 게임 전체의 동작 타임라인.
     * Key: 동작이 시작되는 시간(초), Value: 해당 동작의 코드(actionCode)
     * 2절의 모든 레벨에 대한 동작 정보가 포함될 수 있습니다.
     */
    private Map<Double, Integer> actionTimeline;
}