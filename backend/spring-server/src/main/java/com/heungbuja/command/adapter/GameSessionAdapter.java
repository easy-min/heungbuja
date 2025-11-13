package com.heungbuja.command.adapter;

import com.heungbuja.command.dto.*;
import com.heungbuja.game.dto.ActionTimelineEvent;
import com.heungbuja.game.dto.GameSessionPrepareResponse;
import com.heungbuja.game.dto.SectionInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * game 도메인 DTO를 command 전용 DTO로 변환하는 어댑터
 */
@Slf4j
@Component
public class GameSessionAdapter {

    /**
     * GameSessionPrepareResponse → CommandGameSession 변환
     */
    public CommandGameSession toCommandGameSession(GameSessionPrepareResponse gameResponse) {
        if (gameResponse == null) {
            return null;
        }

        return CommandGameSession.builder()
                .sessionId(gameResponse.getSessionId())
                .songTitle(gameResponse.getSongTitle())
                .songArtist(gameResponse.getSongArtist())
                .tutorialVideoUrl(gameResponse.getTutorialVideoUrl())
                .build();
    }

    /**
     * SectionInfo → CommandSectionInfo 변환 (섹션 시작 시간만)
     */
    public CommandSectionInfo toCommandSectionInfo(SectionInfo sectionInfo) {
        if (sectionInfo == null) {
            return null;
        }

        return CommandSectionInfo.builder()
                .introStartTime(sectionInfo.getIntroStartTime())
                .verse1StartTime(sectionInfo.getVerse1StartTime())
                .breakStartTime(sectionInfo.getBreakStartTime())
                .verse2StartTime(sectionInfo.getVerse2StartTime())
                .build();
    }

    /**
     * SectionInfo → CommandSegmentInfo 변환 (카메라 세그먼트 정보만)
     */
    public CommandSegmentInfo toCommandSegmentInfo(SectionInfo sectionInfo) {
        if (sectionInfo == null) {
            log.error("ERROR: sectionInfo is null!");
            return null;
        }

        log.info("DEBUG: verse1cam={}", sectionInfo.getVerse1cam());
        log.info("DEBUG: verse2cam={}", sectionInfo.getVerse2cam());

        CommandSegmentInfo result = CommandSegmentInfo.builder()
                .verse1cam(toCommandSegmentRange(sectionInfo.getVerse1cam()))
                .verse2cam(toCommandSegmentRange(sectionInfo.getVerse2cam()))
                .build();

        log.info("DEBUG: CommandSegmentInfo created - verse1cam={}, verse2cam={}",
                result.getVerse1cam(), result.getVerse2cam());
        return result;
    }

    /**
     * SectionInfo.VerseInfo → CommandSegmentInfo.SegmentRange 변환
     */
    private CommandSegmentInfo.SegmentRange toCommandSegmentRange(SectionInfo.VerseInfo verseInfo) {
        if (verseInfo == null) {
            return null;
        }

        return CommandSegmentInfo.SegmentRange.builder()
                .startTime(verseInfo.getStartTime())
                .endTime(verseInfo.getEndTime())
                .build();
    }

    /**
     * ActionTimelineEvent → CommandActionTimelineEvent 변환
     */
    public CommandActionTimelineEvent toCommandActionTimelineEvent(ActionTimelineEvent event) {
        if (event == null) {
            return null;
        }

        return new CommandActionTimelineEvent(
                event.getTime(),
                event.getActionCode(),
                event.getActionName()
        );
    }

    /**
     * List<ActionTimelineEvent> → List<CommandActionTimelineEvent> 변환
     */
    public List<CommandActionTimelineEvent> toCommandActionTimelineEvents(List<ActionTimelineEvent> events) {
        if (events == null) {
            return null;
        }

        return events.stream()
                .map(this::toCommandActionTimelineEvent)
                .collect(Collectors.toList());
    }

    /**
     * Map<String, List<ActionTimelineEvent>> → Map<String, List<CommandActionTimelineEvent>> 변환
     */
    public Map<String, List<CommandActionTimelineEvent>> toCommandActionTimelinesMap(
            Map<String, List<ActionTimelineEvent>> timelinesMap) {
        if (timelinesMap == null) {
            return null;
        }

        return timelinesMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toCommandActionTimelineEvents(entry.getValue())
                ));
    }
}
