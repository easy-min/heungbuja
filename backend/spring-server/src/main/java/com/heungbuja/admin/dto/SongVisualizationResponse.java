package com.heungbuja.admin.dto;

import com.heungbuja.game.dto.ActionTimelineEvent;
import com.heungbuja.game.dto.SectionInfo;
import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.domain.SongLyrics;
import com.heungbuja.song.dto.SongGameData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 곡 시각화 응답 DTO
 * - 관리자 페이지에서 곡의 비트, 가사, 동작 타임라인을 시각화하기 위한 데이터
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SongVisualizationResponse {

    /**
     * 곡 ID
     */
    private Long songId;

    /**
     * 비트 정보 (MongoDB)
     * - beats: 각 비트의 정확한 시간 정보
     * - sections: 구간 정보 (intro, verse1, break, verse2)
     * - tempoMap: BPM 정보
     */
    private SongBeat songBeat;

    /**
     * 가사 정보 (MongoDB)
     * - lines: 가사 라인별 시작/종료 시간과 텍스트
     */
    private SongLyrics lyricsInfo;

    /**
     * 구간 정보 (가공됨)
     * - intro/verse1/break/verse2 시작 시간
     * - verse1cam/verse2cam 카메라 녹화 구간
     */
    private SectionInfo sectionInfo;

    /**
     * BPM (Beats Per Minute)
     */
    private Double bpm;

    /**
     * 곡 길이 (초)
     */
    private Double duration;

    /**
     * 1절 동작 타임라인
     * - time: 시간(초)
     * - actionCode: 동작 코드
     * - actionName: 동작 이름
     */
    private List<ActionTimelineEvent> verse1Timeline;

    /**
     * 2절 레벨별 동작 타임라인
     * - key: "level1", "level2", "level3"
     * - value: 동작 타임라인 리스트
     */
    private Map<String, List<ActionTimelineEvent>> verse2Timelines;

    /**
     * SongGameData로부터 변환
     */
    public static SongVisualizationResponse from(SongGameData gameData) {
        return SongVisualizationResponse.builder()
                .songId(gameData.getSongId())
                .songBeat(gameData.getSongBeat())
                .lyricsInfo(gameData.getLyricsInfo())
                .sectionInfo(gameData.getSectionInfo())
                .bpm(gameData.getBpm())
                .duration(gameData.getDuration())
                .verse1Timeline(gameData.getVerse1Timeline())
                .verse2Timelines(gameData.getVerse2Timelines())
                .build();
    }
}
