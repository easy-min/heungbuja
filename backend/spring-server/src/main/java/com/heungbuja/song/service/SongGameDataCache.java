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

        // 4-1. 섹션별 패턴 시퀀스 생성
        com.heungbuja.game.dto.GameStartResponse.SectionPatterns sectionPatterns =
                createSectionPatterns(songBeat, choreography);
        log.info("생성된 sectionPatterns: {}", sectionPatterns);

        // 5. SongGameData 생성 (모든 데이터 포함!)
        SongGameData songGameData = SongGameData.builder()
                .songId(songId)
                .songBeat(songBeat)
                .lyricsInfo(lyricsInfo)
                .sectionInfo(sectionInfo)
                .bpm(songBeat.getTempoMap().get(0).getBpm())
                .duration(songBeat.getAudio().getDurationSec())
                .verse1Timeline(verse1Timeline)
                .verse2Timelines(verse2Timelines)
                .sectionPatterns(sectionPatterns)
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

        // ⭐ 패턴 시퀀스 배열을 순회하며 각 패턴의 시퀀스를 가져옴
        List<List<Integer>> patternSequenceList = new ArrayList<>();
        for (String patternId : verseInfo.getPatternSequence()) {
            List<Integer> patternSeq = findPatternSequenceById(patternData, patternId);
            patternSequenceList.add(patternSeq);
        }

        return generateTimelineForSection(beatNumToTimeMap, section, patternSequenceList, verseInfo.getEachRepeat());
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

        // ⭐ 패턴 시퀀스 배열을 순회하며 각 패턴의 시퀀스를 가져옴
        List<List<Integer>> patternSequenceList = new ArrayList<>();
        for (String patternId : levelInfo.getPatternSequence()) {
            List<Integer> patternSeq = findPatternSequenceById(patternData, patternId);
            patternSequenceList.add(patternSeq);
        }

        return generateTimelineForSection(beatNumToTimeMap, section, patternSequenceList, levelInfo.getEachRepeat());
    }

    /**
     * 실제 타임라인 생성 (공통)
     * 여러 패턴을 병합하여 타임라인을 생성합니다
     */
    private List<ActionTimelineEvent> generateTimelineForSection(
            Map<Integer, Double> beatNumToTimeMap,
            SongBeat.Section section,
            List<List<Integer>> patternSequenceList,
            int eachRepeat) {

        List<ActionTimelineEvent> timeline = new ArrayList<>();
        Map<Integer, String> actionCodeToNameMap = actionRepository.findAll().stream()
                .collect(Collectors.toMap(Action::getActionCode, Action::getName));

        int startBeat = section.getStartBeat();
        int endBeat = section.getEndBeat();

        // ⭐ 1. 패턴 배열을 하나의 큰 패턴으로 병합 (패턴 전체를 eachRepeat번 반복)
        List<Integer> mergedPattern = new ArrayList<>();
        for (int i = 0; i < eachRepeat; i++) {
            for (List<Integer> pattern : patternSequenceList) {
                mergedPattern.addAll(pattern);
            }
        }

        int mergedPatternLength = mergedPattern.size();

        // ⭐ 2. Modulo로 섹션 전체를 채움 (기존 로직 유지)
        for (int currentBeatIndex = startBeat; currentBeatIndex <= endBeat; currentBeatIndex++) {
            int beatWithinSection = currentBeatIndex - startBeat;
            int patternIndex = beatWithinSection % mergedPatternLength;
            int actionCode = mergedPattern.get(patternIndex);

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

    // ===== 섹션 패턴 시퀀스 생성 =====

    /**
     * 섹션별 패턴 시퀀스 생성 (섹션 전체 길이만큼 패턴 반복)
     * 예: verse1이 80비트이고 patternSequence=["P1","P2"], eachRepeat=2라면
     *     ["P1","P1","P2","P2", "P1","P1","P2","P2", ...] (80개)
     */
    private com.heungbuja.game.dto.GameStartResponse.SectionPatterns createSectionPatterns(
            SongBeat songBeat,
            SongChoreography choreography) {

        log.info("createSectionPatterns 시작");
        SongChoreography.Version version = choreography.getVersions().get(0);

        // 1절 패턴 시퀀스 생성 (섹션 전체 길이만큼)
        List<String> verse1Patterns = createFullSectionPatternSequence(
                songBeat,
                version.getVerse1().getPatternSequence(),
                version.getVerse1().getEachRepeat(),
                "verse1"
        );
        log.info("생성된 verse1Patterns 개수: {}", verse1Patterns.size());

        // 2절 레벨별 패턴 시퀀스 생성
        Map<Integer, List<String>> verse2PatternsMap = new HashMap<>();
        for (SongChoreography.VerseLevelPatternInfo levelInfo : version.getVerse2()) {
            List<String> levelPatterns = createFullSectionPatternSequence(
                    songBeat,
                    levelInfo.getPatternSequence(),
                    levelInfo.getEachRepeat(),
                    "verse2"
            );
            verse2PatternsMap.put(levelInfo.getLevel(), levelPatterns);
            log.info("생성된 verse2 level{} Patterns 개수: {}", levelInfo.getLevel(), levelPatterns.size());
        }

        // Verse2Patterns 객체 생성
        com.heungbuja.game.dto.GameStartResponse.Verse2Patterns verse2Patterns =
                com.heungbuja.game.dto.GameStartResponse.Verse2Patterns.builder()
                        .level1(verse2PatternsMap.get(1))
                        .level2(verse2PatternsMap.get(2))
                        .level3(verse2PatternsMap.get(3))
                        .build();

        return com.heungbuja.game.dto.GameStartResponse.SectionPatterns.builder()
                .verse1(verse1Patterns)
                .verse2(verse2Patterns)
                .build();
    }

    /**
     * 섹션 전체 길이만큼 패턴을 반복하여 배열 생성
     */
    private List<String> createFullSectionPatternSequence(
            SongBeat songBeat,
            List<String> patternSequence,
            int eachRepeat,
            String sectionLabel) {

        // 1. 기본 패턴 시퀀스 생성 (패턴 전체를 eachRepeat번 반복)
        List<String> mergedPattern = new ArrayList<>();
        for (int i = 0; i < eachRepeat; i++) {
            for (String patternId : patternSequence) {
                mergedPattern.add(patternId);
            }
        }

        // 2. 섹션의 비트 범위 가져오기
        SongBeat.Section section = findSectionByLabel(songBeat, sectionLabel);
        int sectionBeatCount = section.getEndBeat() - section.getStartBeat() + 1;

        // 3. 섹션 전체 길이만큼 패턴 반복
        List<String> fullPatternSequence = new ArrayList<>();
        int mergedPatternLength = mergedPattern.size();

        for (int i = 0; i < sectionBeatCount; i++) {
            int patternIndex = i % mergedPatternLength;
            fullPatternSequence.add(mergedPattern.get(patternIndex));
        }

        log.info("섹션 {} - 비트 수: {}, 기본 패턴 길이: {}, 최종 패턴 배열 길이: {}",
                sectionLabel, sectionBeatCount, mergedPatternLength, fullPatternSequence.size());

        return fullPatternSequence;
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
                        s -> barStartTimes.getOrDefault(s.getStartBeat(), 0.0)
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

        SectionInfo result = SectionInfo.builder()
                .introStartTime(sectionStartTimes.getOrDefault("intro", 0.0))
                .verse1StartTime(sectionStartTimes.getOrDefault("verse1", 0.0))
                .breakStartTime(sectionStartTimes.getOrDefault("break", 0.0))
                .verse2StartTime(sectionStartTimes.getOrDefault("verse2", 0.0))
                .verse1cam(verse1CamInfo)
                .verse2cam(verse2CamInfo)
                .build();

        log.info("SectionInfo 생성 완료 - verse1cam: {}, verse2cam: {}", verse1CamInfo, verse2CamInfo);
        return result;
    }
}
