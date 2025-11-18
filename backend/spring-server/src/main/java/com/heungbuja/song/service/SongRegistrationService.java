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

        log.info("💾 [Service] registerSongWithAnalysis 시작 - title: {}, artist: {}, mediaId: {}",
                title, artist, media.getId());
        log.info("💾 [Service] beatsNode size: {}, lyricsNode size: {}, choreography size: {}",
                beatsNode != null ? beatsNode.size() : 0,
                lyricsNode != null ? lyricsNode.size() : 0,
                choreographyJson.getSize());

        try {
            // 1. MySQL에 Song 엔티티 생성
            log.info("💾 [Service] 1단계: Song 엔티티 생성 시작");
            Song song = Song.builder()
                    .title(title)
                    .artist(artist)
                    .media(media)
                    .build();

            Song savedSong = songRepository.save(song);
            Long songId = savedSong.getId();

            log.info("💾 [Service] 1단계 완료: Song 생성 완료 - id={}, title={}, artist={}", songId, title, artist);

            // 2. 박자 JSON 파싱 및 MongoDB 저장
            log.info("💾 [Service] 2단계: SongBeat 파싱 및 저장 시작");
            SongBeat songBeat = objectMapper.treeToValue(beatsNode, SongBeat.class);
            songBeat.setSongId(songId);
            songBeatRepository.save(songBeat);
            log.info("💾 [Service] 2단계 완료: SongBeat 저장 완료 - songId={}", songId);

            // 3. 가사 JSON 파싱 및 MongoDB 저장
            log.info("💾 [Service] 3단계: SongLyrics 파싱 및 저장 시작");
            SongLyrics songLyrics = objectMapper.treeToValue(lyricsNode, SongLyrics.class);
            songLyrics.setSongId(songId);
            songLyricsRepository.save(songLyrics);
            log.info("💾 [Service] 3단계 완료: SongLyrics 저장 완료 - songId={}", songId);

            // 4. 안무 JSON 파싱 및 MongoDB 저장
            log.info("💾 [Service] 4단계: SongChoreography 파싱 및 저장 시작");
            SongChoreography songChoreography = parseChoreographyJson(choreographyJson);
            songChoreography.setSongId(songId);
            songChoreographyRepository.save(songChoreography);
            log.info("💾 [Service] 4단계 완료: SongChoreography 저장 완료 - songId={}", songId);

            log.info("💾 [Service] ✅ registerSongWithAnalysis 전체 완료 - songId={}", songId);
            return savedSong;

        } catch (IOException e) {
            log.error("💾 [Service] ❌ JSON 파싱 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "JSON 파일 파싱에 실패했습니다: " + e.getMessage());
        } catch (CustomException e) {
            log.error("💾 [Service] ❌ CustomException 발생: code={}, message={}",
                    e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("💾 [Service] ❌ 곡 등록 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "곡 등록에 실패했습니다: " + e.getMessage());
        }
    }
}
