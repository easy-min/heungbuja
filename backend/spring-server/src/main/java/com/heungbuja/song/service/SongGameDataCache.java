package com.heungbuja.song.service;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.game.dto.ActionTimelineEvent;
import com.heungbuja.game.dto.SectionInfo;
import com.heungbuja.game.entity.Action;
import com.heungbuja.game.repository.jpa.ActionRepository;
import com.heungbuja.song.domain.ChoreographyPattern;
import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.domain.SongChoreography;
import com.heungbuja.song.domain.SongLyrics;
import com.heungbuja.song.dto.SongGameData;
import com.heungbuja.song.repository.mongo.ChoreographyPatternRepository;
import com.heungbuja.song.repository.mongo.SongBeatRepository;
import com.heungbuja.song.repository.mongo.SongChoreographyRepository;
import com.heungbuja.song.repository.mongo.SongLyricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Song별 게임 데이터 캐싱 서비스
 * Beat, Lyrics, SectionInfo + 동작 타임라인 모두 포함!
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongGameDataCache {

    private final RedisTemplate<String, SongGameData> songGameDataRedisTemplate;

    // MongoDB - 게임 기본 데이터
    private final SongBeatRepository songBeatRepository;
    private final SongLyricsRepository songLyricsRepository;

    // MongoDB - 동작 인식 데이터
    private final SongChoreographyRepository songChoreographyRepository;
    private final ChoreographyPatternRepository choreographyPatternRepository;
    private final ActionRepository actionRepository;

    private static final String CACHE_KEY_PREFIX = "song:gamedata:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /**
     * Song 게임 데이터 조회 (캐시 우선)
     * 모든 게임 데이터 포함!
     */
    public SongGameData getOrLoadSongGameData(Long songId) {
        String cacheKey = CACHE_KEY_PREFIX + songId;

        // 캐시 확인
        SongGameData cached = songGameDataRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("캐시 히트: songId={}", songId);
            return cached;
        }

        // 캐시 미스 → MongoDB 조회
        log.info("캐시 미스, MongoDB 조회: songId={}", songId);

        // 1. 기본 데이터 조회
        SongBeat songBeat = songBeatRepository.findBySongId(songId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.GAME_METADATA_NOT_FOUND, "비트 정보를 찾을 수 없습니다"));

        SongLyrics lyricsInfo = songLyricsRepository.findBySongId(songId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.GAME_METADATA_NOT_FOUND, "가사 정보를 찾을 수 없습니다"));

        // 2. 동작 인식 데이터 조회
        SongChoreography choreography = songChoreographyRepository.findBySongId(songId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.GAME_METADATA_NOT_FOUND, "안무 정보를 찾을 수 없습니다"));

        ChoreographyPattern patternData = choreographyPatternRepository.findBySongId(songId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.GAME_METADATA_NOT_FOUND, "안무 패턴 정보를 찾을 수 없습니다"));

        // 3. 데이터 가공
        SectionInfo sectionInfo = processSectionInfo(songBeat);

        // 4. 동작 타임라인 생성 (팀원 로직!)
        Map<Integer, Double> beatNumToTimeMap = songBeat.getBeats().stream()
                .collect(Collectors.toMap(SongBeat.Beat::getI, SongBeat.Beat::getT));

        List<ActionTimelineEvent> verse1Timeline =
                createVerseTimeline(songBeat, choreography, patternData, beatNumToTimeMap, "verse1");

        Map<String, List<ActionTimelineEvent>> verse2Timelines = new HashMap<>();
        choreography.getVersions().get(0).getVerse2().forEach(levelInfo -> {
            String levelKey = "level" + levelInfo.getLevel();
            List<ActionTimelineEvent> levelTimeline =
                    createVerseTimelineForLevel(songBeat, choreography, patternData,
                            beatNumToTimeMap, "verse2", levelInfo);
            verse2Timelines.put(levelKey, levelTimeline);
        });

        // 5. SongGameData 생성 (모든 데이터 포함!)
        SongGameData songGameData = SongGameData.builder()
                .songId(songId)
                .songBeat(songBeat)
                .lyricsInfo(lyricsInfo)
                .sectionInfo(sectionInfo)
                .bpm((int) songBeat.getTempoMap().get(0).getBpm())
                .duration(songBeat.getAudio().getDurationSec())
                .verse1Timeline(verse1Timeline)
                .verse2Timelines(verse2Timelines)
                .cachedAt(LocalDateTime.now())
                .build();

        // 6. 캐싱
        songGameDataRedisTemplate.opsForValue().set(cacheKey, songGameData, CACHE_TTL);
        log.info("Redis 캐싱 완료: songId={}", songId);

        return songGameData;
    }

    // ===== 팀원 로직 (동작 타임라인 생성 5개 메서드) =====

    /**
     * 1절 타임라인 생성
     */
    private List<ActionTimelineEvent> createVerseTimeline(
            SongBeat songBeat,
            SongChoreography choreography,
            ChoreographyPattern patternData,
            Map<Integer, Double> beatNumToTimeMap,
            String sectionLabel) {

        SongChoreography.Version version = choreography.getVersions().get(0);
        SongChoreography.VersePatternInfo verseInfo = version.getVerse1();
        SongBeat.Section section = findSectionByLabel(songBeat, sectionLabel);
        List<Integer> patternSeq = findPatternSequenceById(patternData, verseInfo.getPatternId());

        return generateTimelineForSection(beatNumToTimeMap, section, patternSeq, verseInfo.getRepeat());
    }

    /**
     * 2절 레벨별 타임라인 생성
     */
    private List<ActionTimelineEvent> createVerseTimelineForLevel(
            SongBeat songBeat,
            SongChoreography choreography,
            ChoreographyPattern patternData,
            Map<Integer, Double> beatNumToTimeMap,
            String sectionLabel,
            SongChoreography.VerseLevelPatternInfo levelInfo) {

        SongBeat.Section section = findSectionByLabel(songBeat, sectionLabel);
        List<Integer> patternSeq = findPatternSequenceById(patternData, levelInfo.getPatternId());

        return generateTimelineForSection(beatNumToTimeMap, section, patternSeq, levelInfo.getRepeat());
    }

    /**
     * 실제 타임라인 생성 (공통)
     */
    private List<ActionTimelineEvent> generateTimelineForSection(
            Map<Integer, Double> beatNumToTimeMap,
            SongBeat.Section section,
            List<Integer> patternSequence,
            int repeatCount) {

        List<ActionTimelineEvent> timeline = new ArrayList<>();
        Map<Integer, String> actionCodeToNameMap = actionRepository.findAll().stream()
                .collect(Collectors.toMap(Action::getActionCode, Action::getName));

        int startBeat = section.getStartBeat();
        int endBeat = section.getEndBeat();
        int patternLength = patternSequence.size();

        for (int currentBeatIndex = startBeat; currentBeatIndex <= endBeat; currentBeatIndex++) {
            int beatWithinSection = currentBeatIndex - startBeat;
            int patternIndex = beatWithinSection % patternLength;
            int actionCode = patternSequence.get(patternIndex);

            if (actionCode != 0) {
                double time = beatNumToTimeMap.getOrDefault(currentBeatIndex, -1.0);
                if (time >= 0) {
                    String actionName = actionCodeToNameMap.getOrDefault(actionCode, "알 수 없는 동작");
                    timeline.add(new ActionTimelineEvent(time, actionCode, actionName));
                }
            }
        }
        return timeline;
    }

    /**
     * 패턴 ID로 시퀀스 찾기
     */
    private List<Integer> findPatternSequenceById(ChoreographyPattern patternData, String patternId) {
        return patternData.getPatterns().stream()
                .filter(p -> patternId.equals(p.getPatternId()))
                .findFirst()
                .map(ChoreographyPattern.Pattern::getSequence)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.GAME_METADATA_NOT_FOUND,
                        "안무 패턴 '" + patternId + "'을(를) 찾을 수 없습니다.")
                );
    }

    /**
     * 섹션 레이블로 섹션 찾기
     */
    private SongBeat.Section findSectionByLabel(SongBeat songBeat, String sectionLabel) {
        return songBeat.getSections().stream()
                .filter(s -> sectionLabel.equals(s.getLabel()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("'{}' 섹션을 찾을 수 없습니다. (songId: {})", sectionLabel, songBeat.getSongId());
                    return new CustomException(
                            ErrorCode.GAME_METADATA_NOT_FOUND,
                            "'" + sectionLabel + "' 섹션 정보가 누락되었습니다.");
                });
    }

    // ===== SectionInfo 가공 =====

    /**
     * SectionInfo 가공
     */
    private SectionInfo processSectionInfo(SongBeat songBeat) {
        Map<Integer, Double> beatNumToTimeMap = songBeat.getBeats().stream()
                .collect(Collectors.toMap(SongBeat.Beat::getI, SongBeat.Beat::getT));

        Map<Integer, Double> barStartTimes = songBeat.getBeats().stream()
                .filter(b -> b.getBeat() == 1)
                .collect(Collectors.toMap(SongBeat.Beat::getBar, SongBeat.Beat::getT));

        Map<String, Double> sectionStartTimes = songBeat.getSections().stream()
                .collect(Collectors.toMap(
                        SongBeat.Section::getLabel,
                        s -> barStartTimes.getOrDefault(s.getStartBar(), 0.0)
                ));

        SongBeat.Section verse1Section = findSectionByLabel(songBeat, "verse1");
        SongBeat.Section verse2Section = findSectionByLabel(songBeat, "verse2");

        int verse1CamStartBeat = verse1Section.getStartBeat() + 32;
        int verse1CamEndBeat = verse1CamStartBeat + (16 * 6);
        SectionInfo.VerseInfo verse1CamInfo = SectionInfo.VerseInfo.builder()
                .startTime(beatNumToTimeMap.getOrDefault(verse1CamStartBeat, 0.0))
                .endTime(beatNumToTimeMap.getOrDefault(verse1CamEndBeat, 0.0))
                .build();

        int verse2CamStartBeat = verse2Section.getStartBeat() + 32;
        int verse2CamEndBeat = verse2CamStartBeat + (16 * 6);
        SectionInfo.VerseInfo verse2CamInfo = SectionInfo.VerseInfo.builder()
                .startTime(beatNumToTimeMap.getOrDefault(verse2CamStartBeat, 0.0))
                .endTime(beatNumToTimeMap.getOrDefault(verse2CamEndBeat, 0.0))
                .build();

        return SectionInfo.builder()
                .introStartTime(sectionStartTimes.getOrDefault("intro", 0.0))
                .verse1StartTime(sectionStartTimes.getOrDefault("verse1", 0.0))
                .breakStartTime(sectionStartTimes.getOrDefault("break", 0.0))
                .verse2StartTime(sectionStartTimes.getOrDefault("verse2", 0.0))
                .verse1cam(verse1CamInfo)
                .verse2cam(verse2CamInfo)
                .build();
    }
}
