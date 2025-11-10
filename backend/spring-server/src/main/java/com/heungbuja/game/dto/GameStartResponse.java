package com.heungbuja.game.dto;
import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.domain.SongChoreography;
import com.heungbuja.song.domain.SongLyrics;
import lombok.Builder;
import lombok.Getter;
@Getter
@Builder
public class GameStartResponse {
    /**
     * 이 게임 세션을 식별하는 고유 ID
     */
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
     * 노래의 모든 비트 및 섹션 정보
     */
    private SongBeat beatInfo;

    /**
     * 노래의 모든 가사 정보
     */
    private SongLyrics lyricsInfo;

    /**
     * 노래의 모든 안무 정보 (1, 2, 3단계 포함)
     */
    private SongChoreography choreographyInfo;
}