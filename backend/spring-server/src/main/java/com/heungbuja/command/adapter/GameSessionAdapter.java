package com.heungbuja.command.adapter;

import com.heungbuja.command.dto.*;
import com.heungbuja.game.dto.ActionTimelineEvent;
import com.heungbuja.game.dto.GameSessionPrepareResponse;
import com.heungbuja.game.dto.SectionInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * game 도메인 DTO를 command 전용 DTO로 변환하는 어댑터
 */
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
     * SectionInfo → CommandSectionInfo 변환
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
                .verse1cam(toCommandVerseInfo(sectionInfo.getVerse1cam()))
                .verse2cam(toCommandVerseInfo(sectionInfo.getVerse2cam()))
                .build();
    }

    /**
     * SectionInfo.VerseInfo → CommandSectionInfo.VerseInfo 변환
     */
    private CommandSectionInfo.VerseInfo toCommandVerseInfo(SectionInfo.VerseInfo verseInfo) {
        if (verseInfo == null) {
            return null;
        }

        return CommandSectionInfo.VerseInfo.builder()
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
