package com.heungbuja.game.service;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.game.domain.GameDetail;
import com.heungbuja.game.dto.*;
import com.heungbuja.game.entity.GameResult;
import com.heungbuja.game.enums.GameSessionStatus;
import com.heungbuja.game.repository.mongo.GameDetailRepository;
import com.heungbuja.game.repository.jpa.GameResultRepository;
import com.heungbuja.game.state.GameState;
import com.heungbuja.session.state.ActivityState;
import com.heungbuja.song.domain.ChoreographyPattern;
import com.heungbuja.game.entity.Action;
import com.heungbuja.session.service.SessionStateService;
import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.domain.SongChoreography;
import com.heungbuja.song.domain.SongLyrics;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.mongo.ChoreographyPatternRepository;
import com.heungbuja.song.repository.mongo.SongBeatRepository;
import com.heungbuja.song.repository.mongo.SongChoreographyRepository;
import com.heungbuja.song.repository.mongo.SongLyricsRepository;
import com.heungbuja.song.repository.jpa.SongRepository;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.repository.UserRepository;
import com.heungbuja.game.repository.jpa.ActionRepository;
import com.heungbuja.game.state.GameSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    /**
     * 노래 ID를 Key로, 미리 계산된 '시간-동작코드 타임라인'을 Value로 갖는 캐시
     */
    private final Map<Long, List<ActionTimelineEvent>> choreographyCache = new HashMap<>();

    // --- 상수 정의 ---
    /** 1절을 구성하는 총 세그먼트(묶음)의 수 */
//    private static final int VERSE_1_TOTAL_SEGMENTS = 6;
    /** 게임 전체를 구성하는 총 세그먼트의 수 (1절 + 2절) */
//    private static final int GAME_TOTAL_SEGMENTS = 12;
    /** Redis 세션 만료 시간 (분) */
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    private static final int JUDGMENT_PERFECT = 3;
    private static final double JUDGMENT_BUFFER_SECONDS = 0.2; // 앞뒤로 0.2초의 여유 시간

    // --- Redis Key 접두사 상수 ---
    private static final String GAME_STATE_KEY_PREFIX = "game_state:";
    private static final String GAME_SESSION_KEY_PREFIX = "game_session:";

    // --- application.yml에서 서버 기본 주소 읽어오기 ---
    @Value("${app.base-url:http://localhost:8080/api}") // 기본값은 로컬
    private String baseUrl;

    // --- 의존성 주입 ---
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final SongBeatRepository songBeatRepository;
    private final SongLyricsRepository songLyricsRepository;
    private final SongChoreographyRepository songChoreographyRepository;
    private final RedisTemplate<String, GameState> gameStateRedisTemplate;  // 게임 시작에 필요한 정보
    private final RedisTemplate<String, GameSession> gameSessionRedisTemplate;  // 게임 진행중 점수, 진행 단계

    private final WebClient webClient;
    private final GameResultRepository gameResultRepository;
    private final GameDetailRepository gameDetailRepository;
//    private final MediaUrlService mediaUrlService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionStateService sessionStateService;
//    private final ScoringStrategyFactory scoringStrategyFactory;
    private final ChoreographyPatternRepository choreographyPatternRepository;
    private final ActionRepository actionRepository;

    /**
     * 1. 게임 시작 로직 (디버깅용 - GameState, GameSession 동시 생성)
     */
    @Transactional
    public GameStartResponse startGame(GameStartRequest request) {
        User user = userRepository.findById(request.getUserId()).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.getIsActive()) throw new CustomException(ErrorCode.USER_NOT_ACTIVE);
        Song song = songRepository.findById(request.getSongId()).orElseThrow(() -> new CustomException(ErrorCode.SONG_NOT_FOUND));
        Long songId = song.getId();
        SongBeat songBeat = songBeatRepository.findBySongId(songId).orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "비트 정보를 찾을 수 없습니다."));
        SongLyrics lyricsInfo = songLyricsRepository.findBySongId(songId).orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "가사 정보를 찾을 수 없습니다."));
        SongChoreography choreography = songChoreographyRepository.findBySongId(songId).orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "안무 지시 정보를 찾을 수 없습니다."));
        ChoreographyPattern patternData = choreographyPatternRepository.findBySongId(songId).orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "안무 패턴 정보를 찾을 수 없습니다."));
        log.info(" > 모든 MongoDB 데이터 조회 성공");

        log.info("프론트엔드 응답 데이터 가공을 시작합니다...");
        Map<Integer, Double> beatNumToTimeMap = songBeat.getBeats().stream().collect(Collectors.toMap(SongBeat.Beat::getI, SongBeat.Beat::getT));
        Map<Integer, Double> barStartTimes = songBeat.getBeats().stream().filter(b -> b.getBeat() == 1).collect(Collectors.toMap(SongBeat.Beat::getBar, SongBeat.Beat::getT));
        List<ActionTimelineEvent> verse1Timeline = createVerseTimeline(songBeat, choreography, patternData, beatNumToTimeMap, "verse1");

        // 2절 타임라인을 Map으로 수집
        Map<String, List<ActionTimelineEvent>> verse2TimelinesMap = new HashMap<>();
        choreography.getVersions().get(0).getVerse2().forEach(levelInfo -> {
            String levelKey = "level" + levelInfo.getLevel();
            List<ActionTimelineEvent> levelTimeline = createVerseTimelineForLevel(songBeat, choreography, patternData, beatNumToTimeMap, "verse2", levelInfo);
            verse2TimelinesMap.put(levelKey, levelTimeline);
            log.info(" > 2절 {} 타임라인 생성 완료. 엔트리 개수: {}", levelKey, levelTimeline.size());
        });

        // Verse2Timeline 객체로 변환
        GameStartResponse.Verse2Timeline verse2Timeline = GameStartResponse.Verse2Timeline.builder()
                .level1(verse2TimelinesMap.get("level1"))
                .level2(verse2TimelinesMap.get("level2"))
                .level3(verse2TimelinesMap.get("level3"))
                .build();

        // SectionInfo (Map)와 SegmentInfo 생성
        Map<String, Double> sectionInfo = createSectionInfo(songBeat, barStartTimes);
        GameStartResponse.SegmentRange verse1cam = createSegmentRange(songBeat, "verse1", beatNumToTimeMap);
        GameStartResponse.SegmentRange verse2cam = createSegmentRange(songBeat, "verse2", beatNumToTimeMap);
        GameStartResponse.SegmentInfo segmentInfo = GameStartResponse.SegmentInfo.builder()
                .verse1cam(verse1cam)
                .verse2cam(verse2cam)
                .build();

        String sessionId = UUID.randomUUID().toString();
        String audioUrl = getTestUrl("/media/test");
        Map<String, String> videoUrls = new HashMap<>();
        videoUrls.put("intro", getTestUrl("/media/test/video/break"));
        videoUrls.put("verse1", getTestUrl("/media/test/video/part1"));
        videoUrls.put("verse2_level1", getTestUrl("/media/test/video/part2_1"));
        videoUrls.put("verse2_level2", getTestUrl("/media/test/video/part2_2"));
        videoUrls.put("verse2_level3", "https://example.com/video_v2_level3.mp4");

        GameState gameState = GameState.builder()
                .sessionId(sessionId)
                .userId(user.getId())
                .songId(songId)
                .audioUrl(audioUrl)
                .videoUrls(videoUrls)
                .bpm(songBeat.getTempoMap().get(0).getBpm())
                .duration(songBeat.getAudio().getDurationSec())
                .sectionInfo(sectionInfo)
                .segmentInfo(segmentInfo)
                .lyricsInfo(lyricsInfo.getLines())
                .verse1Timeline(verse1Timeline)
                .verse2Timeline(verse2Timeline)
                .tutorialSuccessCount(0)
                .build();

        GameSession gameSession = GameSession.initial(sessionId, user.getId(), song.getId());

        // <-- (수정) Key가 중복되지 않도록 각각 다른 접두사를 붙여 저장합니다.
        String gameStateKey = GAME_STATE_KEY_PREFIX + sessionId;
        String gameSessionKey = GAME_SESSION_KEY_PREFIX + sessionId;
        gameStateRedisTemplate.opsForValue().set(gameStateKey, gameState, Duration.ofMinutes(SESSION_TIMEOUT_MINUTES));
        gameSessionRedisTemplate.opsForValue().set(gameSessionKey, gameSession, Duration.ofMinutes(SESSION_TIMEOUT_MINUTES));
        log.info("Redis에 GameState와 GameSession 저장 완료: sessionId={}", sessionId);

        sessionStateService.setCurrentActivity(user.getId(), ActivityState.game(sessionId));
        sessionStateService.setSessionStatus(sessionId, "IN_PROGRESS");

        GameResult gameResult = GameResult.builder()
                .user(user)
                .song(song)
                .sessionId(sessionId)
                .status(GameSessionStatus.IN_PROGRESS)
                .startTime(LocalDateTime.now())
                .build();
        gameResultRepository.save(gameResult);
        log.info("새로운 게임 세션 시작: userId={}, sessionId={}", user.getId(), sessionId);

        return GameStartResponse.builder()
                .sessionId(sessionId)
                .songId(song.getId())
                .songTitle(song.getTitle())
                .songArtist(song.getArtist())
                .audioUrl(audioUrl)
                .videoUrls(videoUrls)
                .bpm(songBeat.getTempoMap().get(0).getBpm())
                .duration(songBeat.getAudio().getDurationSec())
                .sectionInfo(sectionInfo)
                .segmentInfo(segmentInfo)
                .lyricsInfo(lyricsInfo.getLines())
                .verse1Timeline(verse1Timeline)
                .verse2Timeline(verse2Timeline)
                .build();
    }

    /**
     * (신규) 1절과 같이 단일 패턴을 가진 절의 전체 타임라인을 생성합니다.
     */
    private List<ActionTimelineEvent> createVerseTimeline(
            SongBeat songBeat, SongChoreography choreography, ChoreographyPattern patternData,
            Map<Integer, Double> beatNumToTimeMap, String sectionLabel) {

        SongChoreography.Version version = choreography.getVersions().get(0);
        SongChoreography.VersePatternInfo verseInfo = version.getVerse1(); // 1절 정보 가져오기
        SongBeat.Section section = findSectionByLabel(songBeat, sectionLabel);
        List<Integer> patternSeq = findPatternSequenceById(patternData, verseInfo.getPatternId());

        return generateTimelineForSection(beatNumToTimeMap, section, patternSeq, verseInfo.getRepeat());
    }

    /**
     * (신규) 2절과 같이 레벨별 패턴을 가진 절의 타임라인을 생성합니다.
     */
    private List<ActionTimelineEvent> createVerseTimelineForLevel(
            SongBeat songBeat, SongChoreography choreography, ChoreographyPattern patternData,
            Map<Integer, Double> beatNumToTimeMap, String sectionLabel,
            SongChoreography.VerseLevelPatternInfo levelInfo) {

        SongBeat.Section section = findSectionByLabel(songBeat, sectionLabel);
        List<Integer> patternSeq = findPatternSequenceById(patternData, levelInfo.getPatternId());

        return generateTimelineForSection(beatNumToTimeMap, section, patternSeq, levelInfo.getRepeat());
    }

    /**
     * (신규) 특정 구간과 동작 시퀀스를 받아 실제 타임라인 리스트를 생성하는 공통 메소드
     */
    private List<ActionTimelineEvent> generateTimelineForSection(
            Map<Integer, Double> beatNumToTimeMap,
            SongBeat.Section section,
            List<Integer> patternSequence,
            int repeatCount) {

        List<ActionTimelineEvent> timeline = new ArrayList<>();
        Map<Integer, String> actionCodeToNameMap = actionRepository.findAll().stream()
                .collect(Collectors.toMap(Action::getActionCode, Action::getName));

        int startBeat = section.getStartBeat();
        int endBeat = section.getEndBeat();
        int patternLength = patternSequence.size();

        for (int currentBeatIndex = startBeat; currentBeatIndex <= endBeat; currentBeatIndex++) {
            int beatWithinSection = currentBeatIndex - startBeat;
            int patternIndex = beatWithinSection % patternLength;
            int actionCode = patternSequence.get(patternIndex);

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
     * SectionInfo 생성을 전담하는 헬퍼 메소드
     */
    private Map<String, Double> createSectionInfo(SongBeat songBeat, Map<Integer, Double> barStartTimes) {
        return songBeat.getSections().stream()
                .collect(Collectors.toMap(
                        SongBeat.Section::getLabel,
                        s -> barStartTimes.getOrDefault(s.getStartBar(), 0.0)
                ));
    }

    /**
     * SegmentRange 생성을 전담하는 헬퍼 메소드
     */
    private GameStartResponse.SegmentRange createSegmentRange(SongBeat songBeat, String verseLabel, Map<Integer, Double> beatNumToTimeMap) {
        SongBeat.Section verseSection = findSectionByLabel(songBeat, verseLabel);
        int camStartBeat = verseSection.getStartBeat() + 32;
        int camEndBeat = camStartBeat + (16 * 6);

        return GameStartResponse.SegmentRange.builder()
                .startTime(beatNumToTimeMap.getOrDefault(camStartBeat, 0.0))
                .endTime(beatNumToTimeMap.getOrDefault(camEndBeat, 0.0))
                .build();
    }

    /**
     * ChoreographyPattern 데이터에서 패턴 ID로 실제 동작 시퀀스를 찾는 헬퍼 메소드
     */
    private List<Integer> findPatternSequenceById(ChoreographyPattern patternData, String patternId) {
        // --- ▼ 임시 디버깅 코드 ▼ ---
//        log.info("찾으려는 patternId: '{}', 길이: {}", patternId, patternId.length());
        if (patternData.getPatterns() != null) {
            patternData.getPatterns().forEach(p -> {
//                log.info("patternData : {}", p);
                String currentId = p.getPatternId(); // getId() 결과를 변수에 먼저 담음
                if (currentId != null) {
                    log.info("DB에 있는 id: '{}', 길이: {}", currentId, currentId.length());
//                    log.info("두 문자열이 같은가? {}", patternId.equals(currentId));
                } else {
                    log.warn("DB에 id가 null인 패턴 데이터가 존재합니다!"); // <-- 이 로그가 찍히는지 확인!
                }
            });
        } else {
            log.error("patternData.getPatterns()가 null입니다!");
        }
        // --- ▲ -------------------- ▲ ---
        return patternData.getPatterns().stream()
                .filter(p -> patternId.equals(p.getPatternId()))
                .findFirst()
                .map(ChoreographyPattern.Pattern::getSequence)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.GAME_METADATA_NOT_FOUND, "안무 패턴 '" + patternId + "'을(를) 찾을 수 없습니다.")
                );
    }

    /**
     * SongBeat 데이터에서 레이블(label)로 특정 섹션 정보를 찾는 헬퍼 메소드
     * @param songBeat 비트 정보 전체가 담긴 객체
     * @param sectionLabel 찾고 싶은 섹션의 이름 (예: "intro", "verse1", "break")
     * @return 찾아낸 Section 객체. 없으면 예외 발생.
     */
    private SongBeat.Section findSectionByLabel(SongBeat songBeat, String sectionLabel) {
        return songBeat.getSections().stream()
                .filter(s -> sectionLabel.equals(s.getLabel()))
                .findFirst()
                .orElseThrow(() -> {
                    // 에러 로그를 남겨서 디버깅이 용이하도록 함
                    log.error("'{}' 섹션을 찾을 수 없습니다. (songId: {})", sectionLabel, songBeat.getSongId());
                    // 프론트엔드에 전달될 명확한 에러 메시지
                    return new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "'" + sectionLabel + "' 섹션 정보가 누락되었습니다.");
                });
    }

    // --- ▼ (신규) 테스트용 URL을 받아오는 헬퍼 메소드 추가 ▼ ---
    private String getTestUrl(String path) {
        try {
            // WebClient를 동기적으로 사용하여 GET 요청을 보내고 결과를 바로 받습니다.
            Map<String, String> response = webClient.get()
                    .uri(baseUrl + path)
                    .retrieve()
                    .bodyToMono(Map.class) // 응답 본문을 Map으로 변환
                    .block(); // 비동기 작업이 끝날 때까지 기다림

            if (response != null && response.containsKey("url")) {
                return response.get("url");
            }
        } catch (Exception e) {
            log.error("테스트 URL({})을 가져오는 데 실패했습니다: {}", path, e.getMessage());
        }
        return "https://example.com/error.mp4"; // 실패 시 반환할 기본 URL
    }

    // ####################################################################
    //                              채점 로직
    // ####################################################################

    /**
     * WebSocket으로부터 받은 단일 프레임을 처리하는 메소드 (최종 구현)
     */
    public void processFrame(WebSocketFrameRequest request) {
        String sessionId = request.getSessionId();
        double currentPlayTime = request.getCurrentPlayTime();

        GameState gameState = getGameState(sessionId);
        GameSession gameSession = getGameSession(sessionId);

        // 마지막 활동 시간을 항상 최신으로 갱신
        gameSession.setLastFrameReceivedTime(Instant.now().toEpochMilli());

        List<ActionTimelineEvent> timeline = getCurrentTimeline(gameState, gameSession);
        int nextActionIndex = gameSession.getNextActionIndex();

        if (nextActionIndex >= timeline.size()) {
            saveGameSession(sessionId, gameSession);
            return;
        }

        ActionTimelineEvent currentAction = timeline.get(nextActionIndex);
        double actionTime = currentAction.getTime();

        // 프레임 수집 로직
        if (currentPlayTime >= actionTime - JUDGMENT_BUFFER_SECONDS &&
                currentPlayTime <= actionTime + JUDGMENT_BUFFER_SECONDS) {
            gameSession.getFrameBuffer().put(currentPlayTime, request.getFrameData());
        }

        // 판정 트리거 로직
        if (currentPlayTime > actionTime + JUDGMENT_BUFFER_SECONDS) {
            if (!gameSession.getFrameBuffer().isEmpty()) {
                int judgment = JUDGMENT_PERFECT;
                log.info("세션 {}의 동작 '{}' 판정 완료 (Mocking): {}점", sessionId, currentAction.getActionName(), judgment);

                sendFeedback(sessionId, judgment, actionTime);
                recordJudgment(sessionId, judgment, gameSession, gameState);
            }

            gameSession.setNextActionIndex(nextActionIndex + 1);
            gameSession.getFrameBuffer().clear();

            // --- ▼ (컴파일 에러 수정) endGame에 sessionId 대신 gameSession 객체를 전달합니다. ---
            if (gameSession.getNextLevel() != null && gameSession.getNextActionIndex() >= timeline.size()) {
                log.info("세션 {}의 2절 모든 동작 판정 완료. 게임을 즉시 종료합니다.", sessionId);
                saveGameSession(sessionId, gameSession); // 마지막 상태를 저장하고
                endGame(gameSession);                    // <-- 수정된 부분!
                return;
            }
            // --- ▲ ------------------------------------------------------------------- ▲ ---
        }

        // 모든 처리 후 마지막에 상태를 한 번만 저장합니다.
        saveGameSession(sessionId, gameSession);
    }

    /**
     * (신규) 1초마다 실행되는 게임 세션 감시자
     * 프레임 수신이 1초 이상 중단된 세션을 찾아 절(verse) 종료 처리를 수행합니다.
     */
    @Scheduled(fixedRate = 1000)
    public void checkGameSessionTimeout() {
        Set<String> sessionKeys = gameSessionRedisTemplate.keys(GAME_SESSION_KEY_PREFIX + "*");
        if (sessionKeys == null || sessionKeys.isEmpty()) {
            return;
        }

        long now = Instant.now().toEpochMilli();

        for (String key : sessionKeys) {
            GameSession session = gameSessionRedisTemplate.opsForValue().get(key);
            if (session == null || session.isProcessing()) {
                continue;
            }

            if (session.getLastFrameReceivedTime() > 0 && now - session.getLastFrameReceivedTime() > 1000) {

                // --- ▼ (핵심 수정) 2절 시작 여부 판단 로직을 단순화합니다. ▼ ---

                if (session.getNextLevel() == null) {
                    // 1절 종료: 아직 레벨이 결정되지 않았고, 타임아웃 발생
                    log.info("세션 {}의 1절 종료 감지. 레벨 결정을 시작합니다.", session.getSessionId());

                    // 처리 중 플래그 설정 (중복 실행 방지)
                    session.setProcessing(true);
                    saveGameSession(session.getSessionId(), session); // 상태 변경 후 저장

                    decideAndSendNextLevel(session.getSessionId());

                } else { // <-- 'else if (isVerse2Started)'를 간단한 'else'로 변경
                    // 2절 종료: 레벨이 결정된 상태에서 타임아웃이 발생하면 무조건 2절 종료로 간주합니다.
                    log.info("세션 {}의 2절 종료 감지. 게임 종료 처리를 시작합니다.", session.getSessionId());

                    // 처리 중 플래그 설정 (중복 실행 방지)
                    session.setProcessing(true);
                    saveGameSession(session.getSessionId(), session); // 상태 변경 후 저장

                    endGame(session);
                }
                // --- ▲ --------------------------------------------------- ▲ ---
            }
        }
    }

    /**
     * (신규) 모인 프레임 묶음을 AI 서버로 보내고, 결과를 처리하는 메소드
     */
    private void callAiServerForJudgment(String sessionId, GameSession gameState, ActionTimelineEvent action, List<String> frames) {
        log.info("세션 {}의 동작 '{}'에 대한 AI 분석 요청 전송. (프레임 {}개)", sessionId, action.getActionName(), frames.size());

        // webClient.post()
        //         .uri("http://motion-server:8000/analyze")
        //         .bodyValue(Map.of(
        //                 "actionCode", action.getActionCode(),
        //                 "actionName", action.getActionName(),
        //                 "frames", frames
        //         ))
        //         .retrieve()
        //         .bodyToMono(AiJudgmentResponse.class) // {"judgment": 3} 같은 응답 가정
        //         .subscribe(
        //             aiResponse -> {
        //                 int judgment = aiResponse.getJudgment();
        //                 log.info(" > AI 분석 결과 수신: {}점", judgment);
        //
        //                 // 판정 결과를 프론트에 WebSocket으로 발송
        //                 sendFeedback(sessionId, judgment, action.getTime());
        //
        //                 // 판정 결과를 Redis의 GameState에 기록
        //                 recordJudgment(sessionId, judgment, action.getTime(), findVerseByTime(gameState.getSongId(), action.getTime()));
        //             },
        //             error -> {
        //                 log.error("AI 서버 호출 실패 (세션 {}): {}", sessionId, error.getMessage());
        //                 sendFeedback(sessionId, 1, action.getTime()); // 1점(BAD)으로 실패 피드백 전송
        //                 recordJudgment(sessionId, 1, action.getTime(), findVerseByTime(gameState.getSongId(), action.getTime()));
        //             }
        //         );
    }

    /**
     * 판정 결과를 Redis('GameSession')에 기록하는 헬퍼 메소드
     */
    private void recordJudgment(String sessionId, int judgment, GameSession currentSession, GameState gameState) {
        // 현재 몇 절인지는 GameSession의 nextLevel 값으로 판단
        int verse = (currentSession.getNextLevel() == null) ? 1 : 2;

        if (verse == 1) {
            currentSession.getVerse1Judgments().add(judgment);
        } else {
            currentSession.getVerse2Judgments().add(judgment);
        }

        // saveGameSession은 processFrame에서 마지막에 한 번만 호출
        log.trace("판정 기록 준비: sessionId={}, judgment={}, verse={}", sessionId, judgment, verse);
    }

    /**
     * 현재 게임 상태에 맞는 타임라인을 선택하는 - 헬퍼 메소드
     */
    private List<ActionTimelineEvent> getCurrentTimeline(GameState gameState, GameSession gameSession) {
        if (gameSession.getNextLevel() == null) {
            // 아직 1절 -> verse1Timeline 반환
            return gameState.getVerse1Timeline();
        } else {
            // 2절 -> 결정된 레벨에 맞는 타임라인을 verse2Timeline 객체에서 가져옴
            int level = gameSession.getNextLevel();
            GameStartResponse.Verse2Timeline verse2Timeline = gameState.getVerse2Timeline();

            List<ActionTimelineEvent> timeline;
            switch (level) {
                case 1:
                    timeline = verse2Timeline.getLevel1();
                    break;
                case 2:
                    timeline = verse2Timeline.getLevel2();
                    break;
                case 3:
                    timeline = verse2Timeline.getLevel3();
                    break;
                default:
                    log.error("세션 {}에 대한 잘못된 레벨 {}이 설정되었습니다.", gameState.getSessionId(), level);
                    return Collections.emptyList();
            }

            if (timeline == null) {
                log.error("세션 {}에 대한 2절 레벨 {}의 타임라인이 null입니다.", gameState.getSessionId(), level);
                return Collections.emptyList();
            }
            return timeline;
        }
    }

    /**
     * 1절 종료 시, 레벨 결정 결과를 WebSocket으로 발송하는 메소드
     */
    public void decideAndSendNextLevel(String sessionId) {
        GameSession gameSession = getGameSession(sessionId);

        double averageScore = calculateScoreFromJudgments(gameSession.getVerse1Judgments());
        int nextLevel = determineLevel(averageScore);

        GameState gameState = getGameState(sessionId);
        String characterVideoUrl = gameState.getVideoUrls().getOrDefault("verse2_level" + nextLevel, "https://example.com/error.mp4");

        gameSession.setNextLevel(nextLevel);
        gameSession.setNextActionIndex(0);

        // --- ▼ (핵심 수정) 세션을 '2절 대기' 상태로 되돌립니다. (타임아웃 검사 비활성화) ---
        gameSession.setLastFrameReceivedTime(0L);
        // 1절 종료 처리가 모두 끝났으므로, '처리 중' 상태를 해제합니다.
        gameSession.setProcessing(false);
        // --- ▲ ------------------------------------------------------------------- ▲ ---

        saveGameSession(sessionId, gameSession); // 모든 상태 변경사항을 한 번에 저장

        LevelDecisionData levelData = new LevelDecisionData(nextLevel, characterVideoUrl);
        GameWebSocketMessage<LevelDecisionData> message = new GameWebSocketMessage<>("LEVEL_DECISION", levelData);
        messagingTemplate.convertAndSend("/topic/game/" + sessionId, message);

        log.info("세션 {}의 다음 레벨 결정: {}, 평균 점수: {}", sessionId, nextLevel, averageScore);
    }


    /**
     * 4. 게임 종료 및 결과 저장
     */
    @Transactional
    public void endGame(GameSession finalSession) {
        if (finalSession == null) {
            log.warn("종료 요청에 전달된 GameSession이 null입니다.");
            return;
        }
        String sessionId = finalSession.getSessionId(); // <-- 세션 객체에서 ID를 가져옴

        double verse1Avg = calculateScoreFromJudgments(finalSession.getVerse1Judgments());
        double verse2Avg = calculateScoreFromJudgments(finalSession.getVerse2Judgments());

        GameDetail.Statistics verse1Stats = calculateStatistics(finalSession.getVerse1Judgments());
        GameDetail.Statistics verse2Stats = calculateStatistics(finalSession.getVerse2Judgments());

        GameDetail gameDetail = GameDetail.builder()
                .sessionId(sessionId)
                .verse1Stats(verse1Stats)
                .verse2Stats(verse2Stats)
                .build();
        gameDetailRepository.save(gameDetail);

        GameResult gameResult = gameResultRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND));

        gameResult.setVerse1AvgScore(verse1Avg);
        gameResult.setVerse2AvgScore(verse2Avg);
        gameResult.setFinalLevel(finalSession.getNextLevel());
        gameResult.complete();

        gameResultRepository.save(gameResult);
        log.info("세션 {}의 게임 결과 저장 완료. 1절 점수: {}, 2절 점수: {}", sessionId, verse1Avg, verse2Avg);

        // <-- (수정) 삭제 시에도 올바른 Key를 사용합니다.
        gameSessionRedisTemplate.delete(GAME_SESSION_KEY_PREFIX + sessionId);
        gameStateRedisTemplate.delete(GAME_STATE_KEY_PREFIX + sessionId); // GameState도 함께 삭제

        sessionStateService.clearSessionStatus(sessionId);
        sessionStateService.clearActivity(finalSession.getUserId());
        log.info("세션 {}의 Redis 데이터 삭제 완료.", sessionId);
    }

    /**
     * 게임 인터럽트 처리 (외부 호출용)
     */
    @Transactional
    public void interruptGame(String sessionId, String reason) {
        GameSession finalSession = getGameSession(sessionId);
        if (finalSession == null) {
            log.warn("존재하지 않는 세션 ID로 인터럽트 요청: {}", sessionId);
            return;
        }
        double verse1Avg = calculateScoreFromJudgments(finalSession.getVerse1Judgments());
        double verse2Avg = calculateScoreFromJudgments(finalSession.getVerse2Judgments());
        GameDetail.Statistics verse1Stats = calculateStatistics(finalSession.getVerse1Judgments());
        GameDetail.Statistics verse2Stats = calculateStatistics(finalSession.getVerse2Judgments());

        GameDetail gameDetail = GameDetail.builder()
                .sessionId(sessionId)
                .verse1Stats(verse1Stats)
                .verse2Stats(verse2Stats)
                .build();
        gameDetailRepository.save(gameDetail);

        GameResult gameResult = gameResultRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND));

        gameResult.setVerse1AvgScore(verse1Avg);
        gameResult.setVerse2AvgScore(verse2Avg);
        gameResult.interrupt(reason);

        gameResultRepository.save(gameResult);
        log.info("세션 {}의 게임 중단 처리 완료. 사유: {}", sessionId, reason);

        // <-- (수정) 삭제 시에도 올바른 Key를 사용합니다.
        gameSessionRedisTemplate.delete(GAME_SESSION_KEY_PREFIX + sessionId);
        gameStateRedisTemplate.delete(GAME_STATE_KEY_PREFIX + sessionId); // GameState도 함께 삭제

        sessionStateService.clearSessionStatus(sessionId);
        sessionStateService.clearActivity(finalSession.getUserId());

        sendGameInterruptNotification(sessionId);
    }

    // ##########################################################
    //                      헬퍼 메서드
    // ##########################################################
    
    // (신규) 판정 리스트를 100점 만점 점수로 변환하는 메소드
    private double calculateScoreFromJudgments(List<Integer> judgments) {
        if (judgments == null || judgments.isEmpty()) {
            return 0.0;
        }
        // 각 판정 점수(1,2,3)를 100점 만점으로 환산 (3점=100, 2점=66.6, 1점=33.3)
        double totalScore = judgments.stream()
                .mapToDouble(judgment -> (double) judgment / 3.0 * 100.0)
                .sum();
        return totalScore / judgments.size();
    }

    // 판정 리스트에서 통계 계산
    private GameDetail.Statistics calculateStatistics(List<Integer> judgments) {
        if (judgments == null || judgments.isEmpty()) {
            return GameDetail.Statistics.builder()
                    .totalMovements(0)
                    .correctMovements(0)
                    .averageScore(0.0)
                    .perfectCount(0)
                    .goodCount(0)
                    .badCount(0)
                    .build();
        }

        int perfectCount = (int) judgments.stream().filter(j -> j == 3).count();
        int goodCount = (int) judgments.stream().filter(j -> j == 2).count();
        int badCount = (int) judgments.stream().filter(j -> j == 1).count();

        return GameDetail.Statistics.builder()
                .totalMovements(judgments.size())
                .correctMovements(perfectCount + goodCount) // PERFECT + GOOD
                .averageScore(calculateScoreFromJudgments(judgments))
                .perfectCount(perfectCount)
                .goodCount(goodCount)
                .badCount(badCount)
                .build();
    }

    // 게임 인터럽트 알림 전송
    private void sendGameInterruptNotification(String sessionId) {
        String destination = "/topic/game/" + sessionId;
        GameWebSocketMessage<Map<String, String>> message = new GameWebSocketMessage<>(
                "GAME_INTERRUPTED",
                Map.of("message", "게임이 중단되었습니다")
        );
        messagingTemplate.convertAndSend(destination, message);
        log.info("게임 중단 알림 전송: sessionId={}", sessionId);
    }

    private GameState getGameState(String sessionId) {
        String key = GAME_STATE_KEY_PREFIX + sessionId; // <-- (수정) 올바른 Key를 사용합니다.
        GameState gameState = gameStateRedisTemplate.opsForValue().get(key);
        if (gameState == null) {
            throw new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND, "GameState not found for key: " + key);
        }
        return gameState;
    }

    public GameSession getGameSession(String sessionId) {
        String key = GAME_SESSION_KEY_PREFIX + sessionId; // <-- (수정) 올바른 Key를 정의합니다.
        GameSession gameSession = gameSessionRedisTemplate.opsForValue().get(key); // <-- (수정) 정의된 Key를 사용합니다.
        if (gameSession == null) {
            log.warn("세션 {}에 대한 GameSession이 없어 새로 생성합니다. (Key: {})", sessionId, key);
            GameState gameState = getGameState(sessionId);
            gameSession = GameSession.initial(sessionId, gameState.getUserId(), gameState.getSongId());
            saveGameSession(sessionId, gameSession); // 새로 생성했으면 저장도 해줍니다.
            return gameSession;
        }
        return gameSession;
    }

    private void saveGameSession(String sessionId, GameSession gameSession) {
        String key = GAME_SESSION_KEY_PREFIX + sessionId; // <-- (수정) 올바른 Key를 정의합니다.
        gameSessionRedisTemplate.opsForValue().set(key, gameSession, Duration.ofMinutes(SESSION_TIMEOUT_MINUTES)); // <-- (수정) 정의된 Key를 사용합니다.
    }

    /**
     * 특정 섹션(part1, part2) 내의 16비트(4마디) 묶음별 시작 시간을 계산하는 헬퍼 메소드
     */
    private List<Double> calculateSegmentStartTimes(SongBeat songBeat, Map<Integer, Double> barStartTimes, String sectionLabel) {
        // 1. 해당 섹션 정보 찾기
        SongBeat.Section targetSection = songBeat.getSections().stream()
                .filter(s -> sectionLabel.equals(s.getLabel()))
                .findFirst()
                .orElse(null);

        if (targetSection == null) {
            log.warn("'{}' 섹션을 찾을 수 없어 세그먼트 시간을 계산할 수 없습니다.", sectionLabel);
            return List.of(); // 빈 리스트 반환
        }

        int startBar = targetSection.getStartBar();
        int endBar = targetSection.getEndBar();
        final int BARS_PER_SEGMENT = 4; // 16비트 = 4마디

        // 2. IntStream을 사용하여 4마디 간격으로 시작 마디 번호를 생성하고, 해당 마디의 시작 시간을 List로 수집
        return IntStream.iterate(startBar, bar -> bar < endBar, bar -> bar + BARS_PER_SEGMENT)
                .mapToObj(barNum -> barStartTimes.getOrDefault(barNum, -1.0))
                .filter(time -> time >= 0) // 혹시 모를 오류(시작 시간을 찾지 못한 경우) 방지
                .collect(Collectors.toList());
    }

    private int determineLevel(double averageScore) {
        if (averageScore >= 80) return 3;
        if (averageScore >= 60) return 2;
        return 1;
    }

    private int findCorrectActionCodeForCurrentTime(Long songId, double currentPlayTime) {
        // TODO: 구현 필요
        return 0;
    }

    // (신규) 실시간 피드백 발송 헬퍼 메소드
    private void sendFeedback(String sessionId, int judgment, double timestamp) {
        String destination = "/topic/game/" + sessionId;
        FeedbackData feedbackData = new FeedbackData(judgment, timestamp);
        GameWebSocketMessage<FeedbackData> message = new GameWebSocketMessage<>("FEEDBACK", feedbackData);
        messagingTemplate.convertAndSend(destination, message);
    }

    // (신규) 점수 판정 로직 헬퍼 메소드 (ScoringStrategy 대체 또는 활용)
    private int determineJudgment(int correctActionCode, int userActionCode) {
        // TODO: 정확도 등을 기반으로 1, 2, 3점 판정하는 로직 구현
        return (correctActionCode == userActionCode) ? 3 : 1; // 임시: 맞으면 3점(PERFECT), 틀리면 1점(BAD)
    }


    // --- 내부 DTO 클래스들 ---
    @Getter
    private static class AiResponse {
        private int actionCode;
    }
}