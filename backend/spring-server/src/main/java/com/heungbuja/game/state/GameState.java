package com.heungbuja.game.state;

import com.heungbuja.game.dto.ActionTimelineEvent;
import com.heungbuja.game.dto.SectionInfo;
import com.heungbuja.song.domain.SongLyrics;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class GameState implements Serializable {
    // 기본 정보
    private String sessionId;
    private Long userId;
    private Long songId;

    // ===== 게임 데이터 =====
    private String audioUrl;
    private Map<String, String> videoUrls;
    private Integer bpm;
    private Double duration;
    private SectionInfo sectionInfo;
    private SongLyrics lyricsInfo;

    // 동작 타임라인
    private List<ActionTimelineEvent> verse1Timeline;
    private Map<String, List<ActionTimelineEvent>> verse2Timelines;

    // ===== 게임 진행 상태 =====
    /** 1절의 각 묶음(16박스)별 채점 결과를 저장하는 리스트 */
    private List<Integer> verse1Judgments;

    /** 2절의 각 묶음별 채점 결과를 저장하는 리스트 */
    private List<Integer> verse2Judgments;

    /** 1절 종료 후 결정된 2절의 안무 레벨 */
    private Integer nextLevel;

    /** 튜토리얼 성공 횟수 */
    private Integer tutorialSuccessCount;

    /**
     * 튜토리얼 성공 횟수 증가
     */
    public void incrementTutorialSuccess() {
        if (this.tutorialSuccessCount == null) {
            this.tutorialSuccessCount = 0;
        }
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
                .verse1Judgments(new ArrayList<>())
                .verse2Judgments(new ArrayList<>())
                .tutorialSuccessCount(0)
                .build();
    }
}