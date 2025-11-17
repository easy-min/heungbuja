package com.heungbuja.admin.controller;

import com.heungbuja.admin.dto.SongListResponse;
import com.heungbuja.admin.dto.SongVisualizationResponse;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.common.security.AdminPrincipal;
import com.heungbuja.song.dto.SongGameData;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.jpa.SongRepository;
import com.heungbuja.song.service.SongGameDataCache;
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
     * 곡 등록
     * - 오디오 파일, JSON 데이터 (박자, 가사, 안무) 업로드
     * - SUPER_ADMIN 권한 필요
     *
     * @param principal 인증된 관리자 정보
     * @param title 곡 제목
     * @param artist 아티스트명
     * @param audioFile 오디오 파일 (.mp3)
     * @param beatJson 박자 정보 JSON 파일
     * @param lyricsJson 가사 정보 JSON 파일
     * @param choreographyJson 안무 정보 JSON 파일
     * @return 생성된 곡 정보
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> createSong(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam("title") String title,
            @RequestParam("artist") String artist,
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam("beatJson") MultipartFile beatJson,
            @RequestParam("lyricsJson") MultipartFile lyricsJson,
            @RequestParam("choreographyJson") MultipartFile choreographyJson) {

        log.info("관리자 {}가 곡 등록 요청: title={}, artist={}", principal.getId(), title, artist);

        // 파일 검증
        if (audioFile.isEmpty() || beatJson.isEmpty() || lyricsJson.isEmpty() || choreographyJson.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "모든 파일을 업로드해주세요.");
        }

        // 파일 형식 검증
        validateFileFormat(audioFile, beatJson, lyricsJson, choreographyJson);

        // TODO: 곡 등록 서비스 호출
        // Song song = songRegistrationService.createSong(title, artist, audioFile, beatJson, lyricsJson, choreographyJson, principal.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "곡 등록 기능은 준비 중입니다.",
                        "title", title,
                        "artist", artist
                ));
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
}
