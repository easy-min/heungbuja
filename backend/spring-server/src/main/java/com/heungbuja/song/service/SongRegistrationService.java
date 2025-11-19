package com.heungbuja.song.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.s3.entity.Media;
import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.domain.SongChoreography;
import com.heungbuja.song.domain.SongLyrics;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.jpa.SongRepository;
import com.heungbuja.song.repository.mongo.SongBeatRepository;
import com.heungbuja.song.repository.mongo.SongChoreographyRepository;
import com.heungbuja.song.repository.mongo.SongLyricsRepository;
import com.heungbuja.song.repository.mongo.ChoreographyPatternRepository;
import com.heungbuja.song.domain.ChoreographyPattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 곡 등록 프로세스 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SongRegistrationService {

    private final SongRepository songRepository;
    private final SongBeatRepository songBeatRepository;
    private final SongLyricsRepository songLyricsRepository;
    private final SongChoreographyRepository songChoreographyRepository;
    private final ChoreographyPatternRepository choreographyPatternRepository;
    private final DefaultChoreographyGenerator defaultChoreographyGenerator;
    private final ObjectMapper objectMapper;

    /**
     * 곡 등록 (MySQL + MongoDB)
     *
     * @param title 곡 제목
     * @param artist 아티스트명
     * @param media Media 엔티티 (오디오 파일)
     * @param beatJson 박자 JSON 파일
     * @param lyricsJson 가사 JSON 파일
     * @param choreographyJson 안무 JSON 파일
     * @return 생성된 Song 엔티티
     */
    @Transactional
    public Song registerSong(
            String title,
            String artist,
            Media media,
            MultipartFile beatJson,
            MultipartFile lyricsJson,
            MultipartFile choreographyJson) {

        try {
            // 1. MySQL에 Song 엔티티 생성
            Song song = Song.builder()
                    .title(title)
                    .artist(artist)
                    .media(media)
                    .build();

            Song savedSong = songRepository.save(song);
            Long songId = savedSong.getId();

            log.info("Song 생성 완료: id={}, title={}, artist={}", songId, title, artist);

            // 2. 박자 JSON 파싱 및 MongoDB 저장
            SongBeat songBeat = parseBeatJson(beatJson);
            songBeat.setSongId(songId);
            songBeatRepository.save(songBeat);
            log.info("SongBeat 저장 완료: songId={}", songId);

            // 3. 가사 JSON 파싱 및 MongoDB 저장
            SongLyrics songLyrics = parseLyricsJson(lyricsJson);
            songLyrics.setSongId(songId);
            songLyricsRepository.save(songLyrics);
            log.info("SongLyrics 저장 완료: songId={}", songId);

            // 4. 안무 JSON 파싱 및 MongoDB 저장
            SongChoreography songChoreography = parseChoreographyJson(choreographyJson);
            songChoreography.setSongId(songId);
            songChoreographyRepository.save(songChoreography);
            log.info("SongChoreography 저장 완료: songId={}", songId);

            return savedSong;

        } catch (IOException e) {
            log.error("JSON 파싱 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "JSON 파일 파싱에 실패했습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("곡 등록 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "곡 등록에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 박자 JSON 파일 파싱
     */
    private SongBeat parseBeatJson(MultipartFile file) throws IOException {
        return objectMapper.readValue(file.getInputStream(), SongBeat.class);
    }

    /**
     * 가사 JSON 파일 파싱
     */
    private SongLyrics parseLyricsJson(MultipartFile file) throws IOException {
        return objectMapper.readValue(file.getInputStream(), SongLyrics.class);
    }

    /**
     * 안무 JSON 파일 파싱
     */
    private SongChoreography parseChoreographyJson(MultipartFile file) throws IOException {
        return objectMapper.readValue(file.getInputStream(), SongChoreography.class);
    }

    /**
     * 곡 등록 (music-server 분석 결과 사용)
     *
     * @param title 곡 제목
     * @param artist 아티스트명
     * @param media Media 엔티티 (오디오 파일)
     * @param beatsNode music-server에서 분석한 박자 JSON
     * @param lyricsNode music-server에서 분석한 가사 JSON
     * @param choreographyJson 안무 JSON 파일
     * @return 생성된 Song 엔티티
     */
    @Transactional
    public Song registerSongWithAnalysis(
            String title,
            String artist,
            Media media,
            JsonNode beatsNode,
            JsonNode lyricsNode,
            MultipartFile choreographyJson) {

        try {
            // 1. MySQL에 Song 엔티티 생성
            Song song = Song.builder()
                    .title(title)
                    .artist(artist)
                    .media(media)
                    .build();

            Song savedSong = songRepository.save(song);
            Long songId = savedSong.getId();

            log.info("Song 생성 완료: id={}, title={}, artist={}", songId, title, artist);

            // 2. 박자 JSON 파싱 및 MongoDB 저장
            SongBeat songBeat = objectMapper.treeToValue(beatsNode, SongBeat.class);
            songBeat.setSongId(songId);
            songBeatRepository.save(songBeat);
            log.info("SongBeat 저장 완료: songId={}", songId);

            // 3. 가사 JSON 파싱 및 MongoDB 저장
            SongLyrics songLyrics = objectMapper.treeToValue(lyricsNode, SongLyrics.class);
            songLyrics.setSongId(songId);
            songLyricsRepository.save(songLyrics);
            log.info("SongLyrics 저장 완료: songId={}", songId);

            // 4. 안무 JSON 파싱 및 MongoDB 저장
            SongChoreography songChoreography = parseChoreographyJson(choreographyJson);
            songChoreography.setSongId(songId);
            songChoreographyRepository.save(songChoreography);
            log.info("SongChoreography 저장 완료: songId={}", songId);

            return savedSong;

        } catch (IOException e) {
            log.error("JSON 파싱 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "JSON 파일 파싱에 실패했습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("곡 등록 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "곡 등록에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 곡 등록 (music-server 분석 결과 사용 + 기본 안무 자동 생성)
     *
     * @param title 곡 제목
     * @param artist 아티스트명
     * @param media Media 엔티티 (오디오 파일)
     * @param beatsNode music-server에서 분석한 박자 JSON
     * @param lyricsNode music-server에서 분석한 가사 JSON
     * @return 생성된 Song 엔티티
     */
    @Transactional
    public Song registerSongWithAnalysisAndDefaultChoreography(
            String title,
            String artist,
            Media media,
            JsonNode beatsNode,
            JsonNode lyricsNode) {

        try {
            // 1. MySQL에 Song 엔티티 생성
            Song song = Song.builder()
                    .title(title)
                    .artist(artist)
                    .media(media)
                    .build();

            Song savedSong = songRepository.save(song);
            Long songId = savedSong.getId();

            log.info("Song 생성 완료: id={}, title={}, artist={}", songId, title, artist);

            // 2. 박자 JSON 파싱 및 MongoDB 저장
            SongBeat songBeat = objectMapper.treeToValue(beatsNode, SongBeat.class);
            songBeat.setSongId(songId);

            // sections가 비어있으면 기본 섹션 추가
            if (songBeat.getSections() == null || songBeat.getSections().isEmpty()) {
                songBeat.setSections(generateDefaultSections(songBeat.getBeats()));
                log.info("기본 섹션 정보 자동 생성: songId={}", songId);
            }

            songBeatRepository.save(songBeat);
            log.info("SongBeat 저장 완료: songId={}", songId);

            // 3. 가사 JSON 파싱 및 MongoDB 저장
            SongLyrics songLyrics = objectMapper.treeToValue(lyricsNode, SongLyrics.class);
            songLyrics.setSongId(songId);
            songLyricsRepository.save(songLyrics);
            log.info("SongLyrics 저장 완료: songId={}", songId);

            // 4. 기본 안무 자동 생성 및 MongoDB 저장
            SongChoreography songChoreography = defaultChoreographyGenerator.generateDefaultSongChoreography(songId);
            songChoreographyRepository.save(songChoreography);
            log.info("SongChoreography 기본값 저장 완료: songId={}", songId);

            // 5. 기본 안무 패턴 자동 생성 및 MongoDB 저장
            ChoreographyPattern choreographyPattern = defaultChoreographyGenerator.generateDefaultChoreographyPattern(songId);
            choreographyPatternRepository.save(choreographyPattern);
            log.info("ChoreographyPattern 기본값 저장 완료: songId={}", songId);

            return savedSong;

        } catch (Exception e) {
            log.error("곡 등록 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "곡 등록에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 기본 섹션 정보 자동 생성
     * - intro: 곡 시작 (0~16 bar)
     * - verse1: 1절 (17~56 bar)
     * - break: 중간 휴식 (57~72 bar)
     * - verse2: 2절 (73~ 끝)
     */
    private java.util.List<SongBeat.Section> generateDefaultSections(java.util.List<SongBeat.Beat> beats) {
        if (beats == null || beats.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        java.util.List<SongBeat.Section> sections = new java.util.ArrayList<>();
        int totalBars = beats.get(beats.size() - 1).getBar();

        // intro (0~16 bar)
        SongBeat.Section intro = new SongBeat.Section();
        intro.setLabel("intro");
        intro.setStartBar(1);
        intro.setEndBar(Math.min(16, totalBars));
        intro.setStartBeat(findBeatByBar(beats, intro.getStartBar()));
        intro.setEndBeat(findBeatByBar(beats, intro.getEndBar()));
        sections.add(intro);

        // verse1 (17~56 bar) - 총 40 bar
        if (totalBars >= 17) {
            SongBeat.Section verse1 = new SongBeat.Section();
            verse1.setLabel("verse1");
            verse1.setStartBar(17);
            verse1.setEndBar(Math.min(56, totalBars));
            verse1.setStartBeat(findBeatByBar(beats, verse1.getStartBar()));
            verse1.setEndBeat(findBeatByBar(beats, verse1.getEndBar()));
            sections.add(verse1);
        }

        // break (57~72 bar) - 총 16 bar
        if (totalBars >= 57) {
            SongBeat.Section breakSection = new SongBeat.Section();
            breakSection.setLabel("break");
            breakSection.setStartBar(57);
            breakSection.setEndBar(Math.min(72, totalBars));
            breakSection.setStartBeat(findBeatByBar(beats, breakSection.getStartBar()));
            breakSection.setEndBeat(findBeatByBar(beats, breakSection.getEndBar()));
            sections.add(breakSection);
        }

        // verse2 (73~ 끝) - 나머지
        if (totalBars >= 73) {
            SongBeat.Section verse2 = new SongBeat.Section();
            verse2.setLabel("verse2");
            verse2.setStartBar(73);
            verse2.setEndBar(totalBars);
            verse2.setStartBeat(findBeatByBar(beats, verse2.getStartBar()));
            verse2.setEndBeat(findBeatByBar(beats, verse2.getEndBar()));
            sections.add(verse2);
        }

        log.info("기본 섹션 생성 완료: {} 섹션, 총 {} bar", sections.size(), totalBars);
        return sections;
    }

    /**
     * 특정 bar의 첫 번째 비트 인덱스 찾기
     */
    private int findBeatByBar(java.util.List<SongBeat.Beat> beats, int bar) {
        for (SongBeat.Beat beat : beats) {
            if (beat.getBar() == bar && beat.getBeat() == 1) {
                return beat.getI();
            }
        }
        // 못 찾으면 해당 bar 범위의 첫 번째 비트 반환
        for (SongBeat.Beat beat : beats) {
            if (beat.getBar() == bar) {
                return beat.getI();
            }
        }
        // 그래도 못 찾으면 마지막 비트
        return beats.isEmpty() ? 1 : beats.get(beats.size() - 1).getI();
    }
}
