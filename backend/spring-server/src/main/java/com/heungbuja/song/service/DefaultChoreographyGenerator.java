package com.heungbuja.song.service;

import com.heungbuja.song.domain.ChoreographyPattern;
import com.heungbuja.song.domain.SongChoreography;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 기본 안무 데이터 자동 생성 서비스
 * - SongChoreography (song_choreographies 컬렉션)
 * - ChoreographyPattern (choreography_patterns 컬렉션)
 */
@Slf4j
@Service
public class DefaultChoreographyGenerator {

    /**
     * 기본 SongChoreography 생성
     *
     * @param songId 곡 ID
     * @return 기본 안무 지시서
     */
    public SongChoreography generateDefaultSongChoreography(Long songId) {
        log.info("기본 SongChoreography 생성: songId={}", songId);

        SongChoreography choreography = new SongChoreography();
        choreography.setSongId(songId);

        // 버전 생성
        SongChoreography.Version version = new SongChoreography.Version();
        version.setId("default_v1");

        // 1절 패턴 설정
        SongChoreography.VersePatternInfo verse1 = new SongChoreography.VersePatternInfo();
        verse1.setPatternSequence(Arrays.asList("P1", "P1", "P1", "P1"));
        verse1.setEachRepeat(1);
        version.setVerse1(verse1);

        // 2절 패턴 설정 (레벨별)
        List<SongChoreography.VerseLevelPatternInfo> verse2List = new ArrayList<>();

        // Level 1
        SongChoreography.VerseLevelPatternInfo level1 = new SongChoreography.VerseLevelPatternInfo();
        level1.setLevel(1);
        level1.setPatternSequence(Arrays.asList("P1", "P1", "P1", "P1"));
        level1.setEachRepeat(1);
        verse2List.add(level1);

        // Level 2
        SongChoreography.VerseLevelPatternInfo level2 = new SongChoreography.VerseLevelPatternInfo();
        level2.setLevel(2);
        level2.setPatternSequence(Arrays.asList("P1", "P2", "P1", "P2"));
        level2.setEachRepeat(1);
        verse2List.add(level2);

        // Level 3
        SongChoreography.VerseLevelPatternInfo level3 = new SongChoreography.VerseLevelPatternInfo();
        level3.setLevel(3);
        level3.setPatternSequence(Arrays.asList("P3", "P3", "P4", "P4"));
        level3.setEachRepeat(1);
        verse2List.add(level3);

        version.setVerse2(verse2List);

        choreography.setVersions(Arrays.asList(version));

        log.info("기본 SongChoreography 생성 완료");
        return choreography;
    }

    /**
     * 기본 ChoreographyPattern 생성
     *
     * @param songId 곡 ID
     * @return 기본 안무 패턴 목록
     */
    public ChoreographyPattern generateDefaultChoreographyPattern(Long songId) {
        log.info("기본 ChoreographyPattern 생성: songId={}", songId);

        ChoreographyPattern choreographyPattern = new ChoreographyPattern();
        choreographyPattern.setSongId(songId);

        List<ChoreographyPattern.Pattern> patterns = new ArrayList<>();

        // P1: 손 박수 + 팔 치기 반복 (16박자)
        ChoreographyPattern.Pattern p1 = new ChoreographyPattern.Pattern();
        p1.setPatternId("P1");
        p1.setDescription("패턴1: 손 박수 + 팔 치기 + 손 박수 + 팔 치기 (16박자)");
        p1.setSequence(Arrays.asList(
            1, 0, 1, 0, 2, 0, 2, 0,
            1, 0, 1, 0, 2, 0, 2, 0
        ));
        patterns.add(p1);

        // P2: 손 박수 + 비상구 (16박자)
        ChoreographyPattern.Pattern p2 = new ChoreographyPattern.Pattern();
        p2.setPatternId("P2");
        p2.setDescription("패턴2: 손 박수 + 비상구 (16박자)");
        p2.setSequence(Arrays.asList(
            1, 0, 1, 0, 1, 0, 1, 0,
            6, 0, 6, 0, 6, 0, 6, 0
        ));
        patterns.add(p2);

        // P3: 손 박수 + 팔 뻗기 + 팔 모으기 + 기우뚱 (16박자)
        ChoreographyPattern.Pattern p3 = new ChoreographyPattern.Pattern();
        p3.setPatternId("P3");
        p3.setDescription("패턴3: 손 박수 + 팔 뻗기 + 팔 모으기 + 기우뚱 (16박자)");
        p3.setSequence(Arrays.asList(
            1, 0, 1, 0, 4, 0, 8, 0,
            4, 0, 8, 0, 5, 0, 5, 0
        ));
        patterns.add(p3);

        // P4: 손 박수 + 비상구 + 손 박수 + 겨드랑이박수 (16박자)
        ChoreographyPattern.Pattern p4 = new ChoreographyPattern.Pattern();
        p4.setPatternId("P4");
        p4.setDescription("패턴4: 손 박수 + 비상구 + 손 박수 + 겨드랑이박수 (16박자)");
        p4.setSequence(Arrays.asList(
            1, 0, 1, 0, 6, 0, 6, 0,
            1, 0, 1, 0, 7, 0, 7, 0
        ));
        patterns.add(p4);

        choreographyPattern.setPatterns(patterns);

        log.info("기본 ChoreographyPattern 생성 완료: {} 패턴", patterns.size());
        return choreographyPattern;
    }
}
