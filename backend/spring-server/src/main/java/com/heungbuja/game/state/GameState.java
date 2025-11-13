package com.heungbuja.game.state;

import com.heungbuja.game.dto.ActionTimelineEvent;
import com.heungbuja.game.dto.GameStartResponse;
import com.heungbuja.song.domain.SongLyrics;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState implements Serializable {
    // 기본 정보
    private String sessionId;
    private Long userId;
    private Long songId;

    // ===== 게임 데이터 =====
    private String audioUrl;
    private Map<String, String> videoUrls;
    private Double bpm;
    private Double duration;
    private Map<String, Double> sectionInfo;
    private GameStartResponse.SegmentInfo segmentInfo;
    private List<SongLyrics.Line> lyricsInfo;

    // 동작 타임라인
    private List<ActionTimelineEvent> verse1Timeline;
    private GameStartResponse.Verse2Timeline verse2Timeline;

    /** 튜토리얼 성공 횟수 */
    @Builder.Default
    private Integer tutorialSuccessCount = 0;

    /**
     * 튜토리얼 성공 횟수 증가
     */
    public void incrementTutorialSuccess() {
        this.tutorialSuccessCount++;
    }

    /**
     * GameState가 처음 생성될 때 호출할 수 있는 정적 팩토리 메소드
     * (기존 코드 호환용 - 사용 안 함)
     */
    public static GameState initial(String sessionId, Long userId, Long songId) {
        return GameState.builder()
                .sessionId(sessionId)
                .userId(userId)
                .songId(songId)
                .tutorialSuccessCount(0)
                .build();
    }
}
