package com.heungbuja.song.dto;

import com.heungbuja.game.dto.ActionTimelineEvent;
import com.heungbuja.game.dto.SectionInfo;
import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.domain.SongLyrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Song별 게임 데이터 (Redis 캐싱용)
 * Beat, Lyrics, SectionInfo + 동작 타임라인 모두 포함
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SongGameData implements Serializable {

    private Long songId;

    // MongoDB 데이터
    private SongBeat songBeat;
    private SongLyrics lyricsInfo;

    // 가공된 데이터
    private SectionInfo sectionInfo;
    private Integer bpm;
    private Double duration;

    // 동작 타임라인
    private List<ActionTimelineEvent> verse1Timeline;
    private Map<String, List<ActionTimelineEvent>> verse2Timelines;

    // 캐싱 시간
    private LocalDateTime cachedAt;
}
