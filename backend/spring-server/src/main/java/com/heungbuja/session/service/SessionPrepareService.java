package com.heungbuja.session.service;

import com.heungbuja.game.dto.GameSessionPrepareResponse;
import com.heungbuja.game.state.GameState;
import com.heungbuja.song.dto.SongGameData;
import com.heungbuja.song.entity.Song;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 세션 준비 서비스
 * GameState 생성 + Redis 저장만!
 * ActivityState는 건드리지 않음 (Command 영역)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionPrepareService {

    // Redis (GameState만!)
    private final RedisTemplate<String, GameState> gameStateRedisTemplate;

    // WebClient
    private final WebClient webClient;

    @Value("${app.base-url:http://localhost:8080/api}")
    private String baseUrl;

    private static final int SESSION_TIMEOUT_MINUTES = 30;

    /**
     * 게임 세션 준비
     * @return sessionId (Command가 ActivityState 설정에 사용)
     */
    @Transactional(readOnly = true)
    public GameSessionPrepareResponse prepareGameSession(
            Long userId,
            Song song,
            String audioUrl,
            SongGameData songGameData) {

        log.info("게임 세션 준비: userId={}, songId={}", userId, song.getId());

        // 1. 비디오 URL 생성
        Map<String, String> videoUrls = generateVideoUrls();

        // 2. sessionId 생성
        String sessionId = UUID.randomUUID().toString();

        // 3. GameState 생성
        GameState gameState = GameState.builder()
                .sessionId(sessionId)
                .userId(userId)
                .songId(song.getId())
                .audioUrl(audioUrl)
                .videoUrls(videoUrls)
                .bpm(songGameData.getBpm())
                .duration(songGameData.getDuration())
                .sectionInfo(songGameData.getSectionInfo())
                .lyricsInfo(songGameData.getLyricsInfo())
                .verse1Timeline(songGameData.getVerse1Timeline())
                .verse2Timelines(songGameData.getVerse2Timelines())
                .verse1Judgments(new ArrayList<>())
                .verse2Judgments(new ArrayList<>())
                .tutorialSuccessCount(0)
                .build();

        // 4. Redis 저장 (GameState만!)
        gameStateRedisTemplate.opsForValue().set(
                sessionId,
                gameState,
                Duration.ofMinutes(SESSION_TIMEOUT_MINUTES)
        );

        log.info("게임 세션 준비 완료: sessionId={}", sessionId);

        // 5. 응답 생성 (sessionId 반환)
        return GameSessionPrepareResponse.builder()
                .sessionId(sessionId)
                .songTitle(song.getTitle())
                .songArtist(song.getArtist())
                .tutorialVideoUrl(videoUrls.get("intro"))
                .build();
    }

    /**
     * 비디오 URL 생성
     */
    private Map<String, String> generateVideoUrls() {
        Map<String, String> videoUrls = new HashMap<>();
        videoUrls.put("intro", getTestUrl("/media/test/video/break"));
        videoUrls.put("verse1", getTestUrl("/media/test/video/part1"));
        videoUrls.put("verse2_level1", getTestUrl("/media/test/video/part2_1"));
        videoUrls.put("verse2_level2", getTestUrl("/media/test/video/part2_2"));
        videoUrls.put("verse2_level3", "https://example.com/video_v2_level3.mp4");
        return videoUrls;
    }

    private String getTestUrl(String path) {
        try {
            Map<String, String> response = webClient.get()
                    .uri(baseUrl + path)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("url")) {
                return response.get("url");
            }
        } catch (Exception e) {
            log.error("테스트 URL 조회 실패: {}", path, e);
        }
        return "https://example.com/error.mp4";
    }
}
