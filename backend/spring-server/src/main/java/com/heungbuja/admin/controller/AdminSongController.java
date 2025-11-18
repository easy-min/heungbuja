package com.heungbuja.admin.controller;

import com.heungbuja.admin.dto.SongListResponse;
import com.heungbuja.admin.dto.SongVisualizationResponse;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.common.security.AdminPrincipal;
import com.heungbuja.s3.entity.Media;
import com.heungbuja.s3.service.MediaService;
import com.heungbuja.song.dto.SongGameData;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.jpa.SongRepository;
import com.heungbuja.song.service.MusicServerClient;
import com.heungbuja.song.service.SongGameDataCache;
import com.heungbuja.song.service.SongRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Admin 전용 Song 관리 API Controller
 * - 곡 목록 조회
 * - 곡 시각화 데이터 조회
 */
@Slf4j
@RestController
@RequestMapping("/admins/songs")
@RequiredArgsConstructor
public class AdminSongController {

    private final SongRepository songRepository;
    private final SongGameDataCache songGameDataCache;
    private final MediaService mediaService;
    private final SongRegistrationService songRegistrationService;
    private final MusicServerClient musicServerClient;

    /**
     * 곡 목록 조회
     * - 모든 곡 목록 반환 (ID, 제목, 아티스트)
     *
     * @param principal 인증된 관리자 정보
     * @return 곡 목록
     */
    @GetMapping
    public ResponseEntity<List<SongListResponse>> getSongs(
            @AuthenticationPrincipal AdminPrincipal principal) {

        log.info("관리자 {}가 곡 목록 조회", principal.getId());

        try {
            List<Song> songs = songRepository.findAll();
            log.info("조회된 곡 개수: {}", songs.size());

            List<SongListResponse> responses = songs.stream()
                    .map(song -> {
                        log.debug("Song 변환: id={}, title={}, artist={}", song.getId(), song.getTitle(), song.getArtist());
                        return SongListResponse.from(song);
                    })
                    .toList();

            log.info("곡 목록 조회 성공: {} 곡", responses.size());
            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("곡 목록 조회 실패", e);
            throw e;
        }
    }

    /**
     * 곡 시각화 데이터 조회
     * - 비트 정보, 가사, 동작 타임라인 등 모든 게임 데이터 반환
     * - 인증된 모든 Admin이 접근 가능
     *
     * @param principal 인증된 관리자 정보
     * @param songId 곡 ID
     * @return 시각화에 필요한 모든 데이터
     */
    @GetMapping("/{songId}/visualization")
    public ResponseEntity<SongVisualizationResponse> getVisualization(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long songId) {

        log.info("관리자 {}가 곡 {} 시각화 데이터 요청", principal.getId(), songId);

        // SongGameData 조회 (캐시 우선)
        SongGameData gameData = songGameDataCache.getOrLoadSongGameData(songId);

        // Response DTO로 변환
        SongVisualizationResponse response = SongVisualizationResponse.from(gameData);

        return ResponseEntity.ok(response);
    }

    /**
     * 곡 등록 (music-server 분석 사용)
     * - 오디오 파일, 가사 텍스트 파일, 안무 JSON 업로드
     * - music-server로 오디오 분석 후 자동으로 박자/가사 JSON 생성
     * - S3 Key는 클라이언트에서 제공 (예: song/파일명.mp3)
     * - SUPER_ADMIN 권한 필요
     *
     * @param principal 인증된 관리자 정보
     * @param title 곡 제목
     * @param artist 아티스트명
     * @param s3Key S3에 업로드된 오디오 파일 Key (예: song/파일명.mp3)
     * @param audioFile 오디오 파일 (.mp3, .wav) - music-server 분석용
     * @param lyricsFile 가사 텍스트 파일 (.txt)
     * @param choreographyJson 안무 정보 JSON 파일
     * @return 생성된 곡 정보
     */
    @PostMapping
    public ResponseEntity<?> createSong(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam("title") String title,
            @RequestParam("artist") String artist,
            @RequestParam("s3Key") String s3Key,
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam("lyricsFile") MultipartFile lyricsFile,
            @RequestParam("choreographyJson") MultipartFile choreographyJson) {

        log.info("관리자 {}가 곡 등록 요청: title={}, artist={}, s3Key={}", principal.getId(), title, artist, s3Key);

        // 파일 검증
        if (audioFile.isEmpty() || lyricsFile.isEmpty() || choreographyJson.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "모든 파일을 업로드해주세요.");
        }

        // 파일 형식 검증
        validateFileFormatWithLyricsText(audioFile, lyricsFile, choreographyJson);

        try {
            // 1. 가사 텍스트 읽기
            String lyricsText = new String(lyricsFile.getBytes(), "UTF-8");

            // 2. music-server로 오디오 분석 요청
            log.info("music-server로 오디오 분석 요청 시작");
            com.fasterxml.jackson.databind.JsonNode analysisResult = musicServerClient.analyzeAudio(audioFile, lyricsText, title);

            com.fasterxml.jackson.databind.JsonNode beatsNode = analysisResult.get("beats");
            com.fasterxml.jackson.databind.JsonNode lyricsNode = analysisResult.get("lyrics");
            log.info("music-server 분석 완료");

            // 3. Media 엔티티 생성 (S3 Key 사용)
            Media media = mediaService.createMedia(title, "MUSIC", s3Key, principal.getId());
            log.info("Media 엔티티 생성 완료: mediaId={}", media.getId());

            // 4. Song 등록 (MySQL + MongoDB)
            Song song = songRegistrationService.registerSongWithAnalysis(
                    title, artist, media, beatsNode, lyricsNode, choreographyJson
            );

            log.info("곡 등록 완료: songId={}, title={}, artist={}", song.getId(), title, artist);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "곡 등록이 완료되었습니다.",
                            "songId", song.getId(),
                            "title", title,
                            "artist", artist
                    ));

        } catch (Exception e) {
            log.error("곡 등록 중 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "곡 등록에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 업로드 파일 형식 검증 (가사 텍스트 버전)
     */
    private void validateFileFormatWithLyricsText(MultipartFile audioFile, MultipartFile lyricsFile,
                                                   MultipartFile choreographyJson) {
        // 오디오 파일 검증
        String audioFileName = audioFile.getOriginalFilename();
        if (audioFileName == null || (!audioFileName.endsWith(".mp3") && !audioFileName.endsWith(".wav"))) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT, "오디오 파일은 .mp3 또는 .wav 형식이어야 합니다.");
        }

        // 가사 텍스트 파일 검증
        String lyricsFileName = lyricsFile.getOriginalFilename();
        if (lyricsFileName == null || !lyricsFileName.endsWith(".txt")) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT, "가사 파일은 .txt 형식이어야 합니다.");
        }

        // 안무 JSON 파일 검증
        validateJsonFile(choreographyJson, "안무 정보");
    }

    /**
     * 업로드 파일 형식 검증
     */
    private void validateFileFormat(MultipartFile audioFile, MultipartFile beatJson,
                                     MultipartFile lyricsJson, MultipartFile choreographyJson) {
        // 오디오 파일 검증
        String audioFileName = audioFile.getOriginalFilename();
        if (audioFileName == null || (!audioFileName.endsWith(".mp3") && !audioFileName.endsWith(".wav"))) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT, "오디오 파일은 .mp3 또는 .wav 형식이어야 합니다.");
        }

        // JSON 파일 검증
        validateJsonFile(beatJson, "박자 정보");
        validateJsonFile(lyricsJson, "가사 정보");
        validateJsonFile(choreographyJson, "안무 정보");
    }

    /**
     * JSON 파일 검증
     */
    private void validateJsonFile(MultipartFile file, String fileType) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.endsWith(".json")) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT,
                    fileType + " 파일은 .json 형식이어야 합니다.");
        }
    }

    /**
     * 곡 등록 (music-server 분석 + 기본 안무 자동 생성)
     * - 오디오 파일, 가사 텍스트 파일만 업로드
     * - music-server로 오디오 분석 후 자동으로 박자/가사 JSON 생성
     * - 안무는 기본 패턴으로 자동 생성
     * - S3 Key는 클라이언트에서 제공 (예: song/파일명.mp3)
     * - SUPER_ADMIN 권한 필요
     *
     * @param principal 인증된 관리자 정보
     * @param title 곡 제목
     * @param artist 아티스트명
     * @param s3Key S3에 업로드된 오디오 파일 Key (예: song/파일명.mp3)
     * @param audioFile 오디오 파일 (.mp3, .wav) - music-server 분석용
     * @param lyricsFile 가사 텍스트 파일 (.txt)
     * @return 생성된 곡 정보
     */
    @PostMapping("/auto")
    public ResponseEntity<?> createSongWithAutoChoreography(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam("title") String title,
            @RequestParam("artist") String artist,
            @RequestParam("s3Key") String s3Key,
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam("lyricsFile") MultipartFile lyricsFile) {

        log.info("관리자 {}가 곡 등록 요청 (기본 안무 자동 생성): title={}, artist={}, s3Key={}",
                 principal.getId(), title, artist, s3Key);

        // 파일 검증
        if (audioFile.isEmpty() || lyricsFile.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "오디오 파일과 가사 파일을 업로드해주세요.");
        }

        // 파일 형식 검증
        validateAudioAndLyricsFiles(audioFile, lyricsFile);

        try {
            // 1. 가사 텍스트 읽기
            String lyricsText = new String(lyricsFile.getBytes(), "UTF-8");

            // 2. music-server로 오디오 분석 요청
            log.info("music-server로 오디오 분석 요청 시작");
            com.fasterxml.jackson.databind.JsonNode analysisResult = musicServerClient.analyzeAudio(audioFile, lyricsText, title);

            com.fasterxml.jackson.databind.JsonNode beatsNode = analysisResult.get("beats");
            com.fasterxml.jackson.databind.JsonNode lyricsNode = analysisResult.get("lyrics");
            log.info("music-server 분석 완료");

            // 3. Media 엔티티 생성 (S3 Key 사용)
            Media media = mediaService.createMedia(title, "MUSIC", s3Key, principal.getId());
            log.info("Media 엔티티 생성 완료: mediaId={}", media.getId());

            // 4. Song 등록 (MySQL + MongoDB) - 기본 안무 자동 생성
            Song song = songRegistrationService.registerSongWithAnalysisAndDefaultChoreography(
                    title, artist, media, beatsNode, lyricsNode
            );

            log.info("곡 등록 완료 (기본 안무 포함): songId={}, title={}, artist={}", song.getId(), title, artist);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "곡 등록이 완료되었습니다 (기본 안무 자동 생성).",
                            "songId", song.getId(),
                            "title", title,
                            "artist", artist
                    ));

        } catch (Exception e) {
            log.error("곡 등록 중 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "곡 등록에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 오디오 및 가사 파일 형식 검증
     */
    private void validateAudioAndLyricsFiles(MultipartFile audioFile, MultipartFile lyricsFile) {
        // 오디오 파일 검증
        String audioFileName = audioFile.getOriginalFilename();
        if (audioFileName == null || (!audioFileName.endsWith(".mp3") && !audioFileName.endsWith(".wav"))) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT, "오디오 파일은 .mp3 또는 .wav 형식이어야 합니다.");
        }

        // 가사 텍스트 파일 검증
        String lyricsFileName = lyricsFile.getOriginalFilename();
        if (lyricsFileName == null || !lyricsFileName.endsWith(".txt")) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT, "가사 파일은 .txt 형식이어야 합니다.");
        }
    }

    /**
     * 곡 분석 (music-server 분석 결과만 반환)
     * - 등록하지 않고 분석 결과만 확인
     *
     * @param principal 인증된 관리자 정보
     * @param title 곡 제목
     * @param audioFile 오디오 파일 (.mp3, .wav)
     * @param lyricsFile 가사 텍스트 파일 (.txt)
     * @return 분석 결과 (박자 + 가사 JSON)
     */
    @PostMapping("/analyze-only")
    public ResponseEntity<?> analyzeSongOnly(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam("title") String title,
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam("lyricsFile") MultipartFile lyricsFile) {

        log.info("관리자 {}가 곡 분석 요청 (등록 안함): title={}", principal.getId(), title);

        // 파일 검증
        if (audioFile.isEmpty() || lyricsFile.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "오디오 파일과 가사 파일을 업로드해주세요.");
        }

        validateAudioAndLyricsFiles(audioFile, lyricsFile);

        try {
            // 가사 텍스트 읽기
            String lyricsText = new String(lyricsFile.getBytes(), "UTF-8");

            // music-server로 오디오 분석 요청
            log.info("music-server로 오디오 분석 요청 시작");
            com.fasterxml.jackson.databind.JsonNode analysisResult = musicServerClient.analyzeAudio(audioFile, lyricsText, title);

            log.info("music-server 분석 완료");

            // 분석 결과 그대로 반환
            return ResponseEntity.ok(analysisResult);

        } catch (Exception e) {
            log.error("곡 분석 중 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.SONG_REGISTRATION_FAILED, "곡 분석에 실패했습니다: " + e.getMessage());
        }
    }
}
