package com.heungbuja.admin.controller;

import com.heungbuja.admin.dto.SongListResponse;
import com.heungbuja.admin.dto.SongVisualizationResponse;
import com.heungbuja.common.security.AdminPrincipal;
import com.heungbuja.song.dto.SongGameData;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.jpa.SongRepository;
import com.heungbuja.song.service.SongGameDataCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin 전용 Song 관리 API Controller
 * - 곡 목록 조회
 * - 곡 시각화 데이터 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/songs")
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

        List<Song> songs = songRepository.findAll();
        List<SongListResponse> responses = songs.stream()
                .map(SongListResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
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
}
