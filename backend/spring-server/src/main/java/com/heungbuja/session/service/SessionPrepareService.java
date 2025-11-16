package com.heungbuja.session.service;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.game.dto.GameSessionPrepareResponse;
import com.heungbuja.game.dto.GameStartResponse;
import com.heungbuja.game.dto.SectionInfo;
import com.heungbuja.game.entity.GameResult;
import com.heungbuja.game.enums.GameSessionStatus;
import com.heungbuja.game.repository.jpa.GameResultRepository;
import com.heungbuja.game.state.GameSession;
import com.heungbuja.game.state.GameState;
import com.heungbuja.session.state.ActivityState;
import com.heungbuja.song.domain.SongChoreography;
import com.heungbuja.song.dto.SongGameData;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.mongo.SongChoreographyRepository;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
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

    private final UserRepository userRepository;
    private final GameResultRepository gameResultRepository;
    private final SongChoreographyRepository songChoreographyRepository;

    // Redis
    private final RedisTemplate<String, GameState> gameStateRedisTemplate;
    private final RedisTemplate<String, GameSession> gameSessionRedisTemplate;

    // Session State 서비스
    private final SessionStateService sessionStateService;

    private static final int SESSION_TIMEOUT_MINUTES = 30;
    private static final String GAME_STATE_KEY_PREFIX = "game_state:";
    private static final String GAME_SESSION_KEY_PREFIX = "game_session:";

    /**
     * 게임 세션 준비
     * @return sessionId (Command가 ActivityState 설정에 사용)
     */
    @Transactional
    public GameSessionPrepareResponse prepareGameSession(
            Long userId,
            Song song,
            String audioUrl,
            SongGameData songGameData) {

        log.info("게임 세션 준비: userId={}, songId={}", userId, song.getId());
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1. 비디오 URL 생성 (패턴 기반)
        Map<String, String> videoUrls = generateVideoUrls(song.getId());

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

        GameResult gameResult = GameResult.builder()
                .user(user)
                .song(song)
                .sessionId(sessionId)
                .status(GameSessionStatus.IN_PROGRESS)
                .startTime(LocalDateTime.now())
                .build();
        gameResultRepository.save(gameResult);
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
     * 비디오 URL 생성 (패턴 기반)
     */
    private Map<String, String> generateVideoUrls(Long songId) {
        Map<String, String> videoUrls = new HashMap<>();

        // SongChoreography 조회
        SongChoreography choreography = songChoreographyRepository.findBySongId(songId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.GAME_METADATA_NOT_FOUND, "안무 정보를 찾을 수 없습니다"));

        SongChoreography.Version version = choreography.getVersions().get(0);

        // intro: 공통 튜토리얼
        videoUrls.put("intro", "video/break.mp4");

        // verse1: 첫 번째 패턴
        String verse1PatternId = version.getVerse1().getPatternSequence().get(0);
        videoUrls.put("verse1", convertPatternIdToVideoUrl(verse1PatternId));

        // verse2: 각 레벨의 첫 번째 패턴
        for (SongChoreography.VerseLevelPatternInfo levelInfo : version.getVerse2()) {
            String patternId = levelInfo.getPatternSequence().get(0);
            String key = "verse2_level" + levelInfo.getLevel();
            videoUrls.put(key, convertPatternIdToVideoUrl(patternId));
        }

        return videoUrls;
    }

    /**
     * 패턴 ID → 비디오 URL 변환
     * TODO: 패턴별 비디오 준비 완료 시 임시 매핑 제거하고 "video/pattern_" + patternId.toLowerCase() + ".mp4" 사용
     */
    private String convertPatternIdToVideoUrl(String patternId) {
        // 임시 매핑: 현재 존재하는 비디오 파일 사용
        switch (patternId) {
            case "P1":
                return "video/part1.mp4";
            case "P2":
                return "video/part2_level1.mp4";
            case "P3":
                return "video/part2_level2.mp4";
            case "P4":
                return "video/part1.mp4";  // 반복
            default:
                log.warn("알 수 없는 패턴 ID: {}. 기본 비디오 사용", patternId);
                return "video/part1.mp4";
        }

        // 나중에 패턴별 비디오 준비되면 아래 코드로 교체:
        // return "video/pattern_" + patternId.toLowerCase() + ".mp4";
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
