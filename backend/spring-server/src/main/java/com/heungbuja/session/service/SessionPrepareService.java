package com.heungbuja.session.service;

import com.heungbuja.game.dto.GameSessionPrepareResponse;
import com.heungbuja.game.dto.GameStartResponse;
import com.heungbuja.game.dto.SectionInfo;
import com.heungbuja.game.state.GameSession;
import com.heungbuja.game.state.GameState;
import com.heungbuja.session.state.ActivityState;
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
 * GameState + GameSession 생성 + Redis 저장 + ActivityState 설정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionPrepareService {

    // Redis
    private final RedisTemplate<String, GameState> gameStateRedisTemplate;
    private final RedisTemplate<String, GameSession> gameSessionRedisTemplate;

    // Session State 서비스
    private final SessionStateService sessionStateService;

    // WebClient
    private final WebClient webClient;

    @Value("${app.base-url:http://localhost:8080/api}")
    private String baseUrl;

    private static final int SESSION_TIMEOUT_MINUTES = 30;
    private static final String GAME_STATE_KEY_PREFIX = "game_state:";
    private static final String GAME_SESSION_KEY_PREFIX = "game_session:";

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

        // 3. SectionInfo 변환
        SectionInfo srcSectionInfo = songGameData.getSectionInfo();
        Map<String, Double> sectionInfoMap = convertSectionInfoToMap(srcSectionInfo);
        GameStartResponse.SegmentInfo segmentInfo = convertToSegmentInfo(srcSectionInfo);

        // 4. Verse2Timeline 변환
        GameStartResponse.Verse2Timeline verse2Timeline = GameStartResponse.Verse2Timeline.builder()
                .level1(songGameData.getVerse2Timelines().get("level1"))
                .level2(songGameData.getVerse2Timelines().get("level2"))
                .level3(songGameData.getVerse2Timelines().get("level3"))
                .build();

        // 5. GameState 생성
        GameState gameState = GameState.builder()
                .sessionId(sessionId)
                .userId(userId)
                .songId(song.getId())
                .audioUrl(audioUrl)
                .videoUrls(videoUrls)
                .bpm(songGameData.getBpm())
                .duration(songGameData.getDuration())
                .sectionInfo(sectionInfoMap)
                .segmentInfo(segmentInfo)
                .lyricsInfo(songGameData.getLyricsInfo().getLines())
                .verse1Timeline(songGameData.getVerse1Timeline())
                .verse2Timeline(verse2Timeline)
                .tutorialSuccessCount(0)
                .build();

        // 4. GameSession 생성
        GameSession gameSession = GameSession.initial(sessionId, userId, song.getId());

        // 5. Redis 저장 (GameState + GameSession)
        String gameStateKey = GAME_STATE_KEY_PREFIX + sessionId;
        String gameSessionKey = GAME_SESSION_KEY_PREFIX + sessionId;
        gameStateRedisTemplate.opsForValue().set(
                gameStateKey,
                gameState,
                Duration.ofMinutes(SESSION_TIMEOUT_MINUTES)
        );
        gameSessionRedisTemplate.opsForValue().set(
                gameSessionKey,
                gameSession,
                Duration.ofMinutes(SESSION_TIMEOUT_MINUTES)
        );
        log.info("Redis에 GameState와 GameSession 저장 완료: sessionId={}", sessionId);

        // 6. ActivityState 설정
        sessionStateService.setCurrentActivity(userId, ActivityState.game(sessionId));
        sessionStateService.setSessionStatus(sessionId, "IN_PROGRESS");

        log.info("게임 세션 준비 완료: sessionId={}", sessionId);

        // 7. 응답 생성 (sessionId 반환)
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

    /**
     * SectionInfo → Map<String, Double> 변환
     */
    private Map<String, Double> convertSectionInfoToMap(SectionInfo sectionInfo) {
        Map<String, Double> map = new HashMap<>();
        map.put("intro", sectionInfo.getIntroStartTime());
        map.put("verse1", sectionInfo.getVerse1StartTime());
        map.put("break", sectionInfo.getBreakStartTime());
        map.put("verse2", sectionInfo.getVerse2StartTime());
        return map;
    }

    /**
     * SectionInfo → SegmentInfo 변환
     */
    private GameStartResponse.SegmentInfo convertToSegmentInfo(SectionInfo sectionInfo) {
        GameStartResponse.SegmentRange verse1cam = GameStartResponse.SegmentRange.builder()
                .startTime(sectionInfo.getVerse1cam().getStartTime())
                .endTime(sectionInfo.getVerse1cam().getEndTime())
                .build();

        GameStartResponse.SegmentRange verse2cam = GameStartResponse.SegmentRange.builder()
                .startTime(sectionInfo.getVerse2cam().getStartTime())
                .endTime(sectionInfo.getVerse2cam().getEndTime())
                .build();

        return GameStartResponse.SegmentInfo.builder()
                .verse1cam(verse1cam)
                .verse2cam(verse2cam)
                .build();
    }
}
