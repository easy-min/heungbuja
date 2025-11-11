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
import com.heungbuja.song.domain.ChoreographyPattern;
import com.heungbuja.game.entity.Action;
import com.heungbuja.session.service.SessionStateService;
import com.heungbuja.session.state.ActivityState;
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.*;
import java.time.LocalDateTime;
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
    // --- application.yml에서 서버 기본 주소 읽어오기 ---
    @Value("${app.base-url:http://localhost:8080/api}") // 기본값은 로컬
    private String baseUrl;

    // --- 의존성 주입 ---
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final SongBeatRepository songBeatRepository;
    private final SongLyricsRepository songLyricsRepository;
    private final SongChoreographyRepository songChoreographyRepository;
    private final RedisTemplate<String, GameState> gameStateRedisTemplate;
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
     * 1. 게임 시작 로직
     */
    @Transactional
    public GameStartResponse startGame(GameStartRequest request) {
        // --- 1-1 & 1-2. 사용자 및 노래 정보 조회 ---
        User user = userRepository.findById(request.getUserId()).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.getIsActive()) throw new CustomException(ErrorCode.USER_NOT_ACTIVE);
        Song song = songRepository.findById(request.getSongId()).orElseThrow(() -> new CustomException(ErrorCode.SONG_NOT_FOUND));
        Long songId = song.getId();

        // --- 1-3. MongoDB에서 모든 게임 메타데이터 조회 ---
        log.info("MongoDB에서 songId={}에 대한 메타데이터 조회를 시작합니다.", songId);
        SongBeat songBeat = songBeatRepository.findBySongId(songId).orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "비트 정보를 찾을 수 없습니다."));
        SongLyrics lyricsInfo = songLyricsRepository.findBySongId(songId).orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "가사 정보를 찾을 수 없습니다."));
        SongChoreography choreography = songChoreographyRepository.findBySongId(songId).orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "안무 지시 정보를 찾을 수 없습니다."));
        ChoreographyPattern patternData = choreographyPatternRepository.findBySongId(songId).orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "안무 패턴 정보를 찾을 수 없습니다."));
        log.info(" > 모든 MongoDB 데이터 조회 성공");

        // --- 1-4. (핵심) 데이터 가공 로직 ---
        log.info("프론트엔드 응답 데이터 가공을 시작합니다...");

        // A. 시간 계산에 필요한 Map들을 한 번만 생성
        Map<Integer, Double> beatNumToTimeMap = songBeat.getBeats().stream().collect(Collectors.toMap(SongBeat.Beat::getI, SongBeat.Beat::getT));
        Map<Integer, Double> barStartTimes = songBeat.getBeats().stream().filter(b -> b.getBeat() == 1).collect(Collectors.toMap(SongBeat.Beat::getBar, SongBeat.Beat::getT));

        // B. 1절 타임라인 생성
        List<ActionTimelineEvent> verse1Timeline = createVerseTimeline(songBeat, choreography, patternData, beatNumToTimeMap, "verse1");
        log.info(" > 1절 동작 타임라인 생성 완료. 엔트리 개수: {}", verse1Timeline.size());

        // C. 2절 레벨별 타임라인 생성
        Map<String, List<ActionTimelineEvent>> verse2Timelines = new HashMap<>();
        choreography.getVersions().get(0).getVerse2().forEach(levelInfo -> {
            String levelKey = "level" + levelInfo.getLevel();
            List<ActionTimelineEvent> levelTimeline = createVerseTimelineForLevel(songBeat, choreography, patternData, beatNumToTimeMap, "verse2", levelInfo);
            verse2Timelines.put(levelKey, levelTimeline);
            log.info(" > 2절 {} 타임라인 생성 완료. 엔트리 개수: {}", levelKey, levelTimeline.size());
        });

        // D. SectionInfo 객체 생성
        SectionInfo sectionInfo = createSectionInfo(songBeat, barStartTimes, beatNumToTimeMap);
        log.info(" > SectionInfo 생성 완료.");

        // --- 1-5. 세션 생성 및 URL 발급 ---
        String sessionId = UUID.randomUUID().toString();

        // Redis: 게임 상태 저장
        GameState initialGameState = GameState.initial(sessionId, user.getId(), song.getId());
        saveGameState(sessionId, initialGameState);
        log.info("새로운 게임 세션 시작: userId={}, sessionId={}", user.getId(), sessionId);

        // Redis: 활동 상태 설정
        sessionStateService.setCurrentActivity(user.getId(), ActivityState.game(sessionId));
        sessionStateService.setSessionStatus(sessionId, "IN_PROGRESS");

        // MySQL: 게임 결과 초기 레코드 생성
        GameResult gameResult = GameResult.builder()
                .user(user)
                .song(song)
                .sessionId(sessionId)
                .status(GameSessionStatus.IN_PROGRESS)
                .startTime(LocalDateTime.now())
                .build();
        gameResultRepository.save(gameResult);

        log.info("새로운 게임 세션 시작: userId={}, sessionId={}", user.getId(), sessionId);

        String audioUrl = getTestUrl("/media/test");
        Map<String, String> videoUrls = new HashMap<>();
        videoUrls.put("intro", getTestUrl("/media/test/video/break"));
        videoUrls.put("verse1", getTestUrl("/media/test/video/part1"));
        videoUrls.put("verse2_level1", getTestUrl("/media/test/video/part2_1"));
        videoUrls.put("verse2_level2", getTestUrl("/media/test/video/part2_2"));
        videoUrls.put("verse2_level3", "https://example.com/video_v2_level3.mp4");

        // --- 1-6. 최종 응답 생성 ---
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
                .lyricsInfo(lyricsInfo)
                .verse1Timeline(verse1Timeline)
                .verse2Timelines(verse2Timelines)
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
    private SectionInfo createSectionInfo(SongBeat songBeat, Map<Integer, Double> barStartTimes, Map<Integer, Double> beatNumToTimeMap) {
        // --- ▼ (핵심 수정) 이 메소드 내부에서 beatNumToTimeMap을 생성하는 코드를 삭제하고, 파라미터를 그대로 사용합니다. ▼ ---

        Map<String, Double> sectionStartTimes = songBeat.getSections().stream()
                .collect(Collectors.toMap(SongBeat.Section::getLabel, s -> barStartTimes.getOrDefault(s.getStartBar(), 0.0)));

        SongBeat.Section verse1Section = findSectionByLabel(songBeat, "verse1");
        SongBeat.Section verse2Section = findSectionByLabel(songBeat, "verse2");

        int verse1CamStartBeat = verse1Section.getStartBeat() + 32;
        int verse1CamEndBeat = verse1CamStartBeat + (16 * 6);
        SectionInfo.VerseInfo verse1CamInfo = SectionInfo.VerseInfo.builder()
                // 파라미터로 받은 beatNumToTimeMap을 사용
                .startTime(beatNumToTimeMap.getOrDefault(verse1CamStartBeat, 0.0))
                .endTime(beatNumToTimeMap.getOrDefault(verse1CamEndBeat, 0.0))
                .build();

        int verse2CamStartBeat = verse2Section.getStartBeat() + 32;
        int verse2CamEndBeat = verse2CamStartBeat + (16 * 6);
        SectionInfo.VerseInfo verse2CamInfo = SectionInfo.VerseInfo.builder()
                // 파라미터로 받은 beatNumToTimeMap을 사용
                .startTime(beatNumToTimeMap.getOrDefault(verse2CamStartBeat, 0.0))
                .endTime(beatNumToTimeMap.getOrDefault(verse2CamEndBeat, 0.0))
                .build();

        return SectionInfo.builder()
                .introStartTime(sectionStartTimes.getOrDefault("intro", 0.0))
                .verse1StartTime(sectionStartTimes.getOrDefault("verse1", 0.0))
                .breakStartTime(sectionStartTimes.getOrDefault("break", 0.0))
                .verse2StartTime(sectionStartTimes.getOrDefault("verse2", 0.0))
                .verse1cam(verse1CamInfo)
                .verse2cam(verse2CamInfo)
                .build();

        // --- ▲ ------------------------------------------------------------------------------------------ ▲ ---
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

    /**
     * 2. WebSocket으로부터 받은 단일 프레임을 처리하는 메소드
     */
    public void processFrame(WebSocketFrameRequest request) {
        String sessionId = request.getSessionId();

        // 인터럽트 체크 (최우선)
        String status = sessionStateService.getSessionStatus(sessionId);
        if ("INTERRUPTING".equals(status) || "INTERRUPTED".equals(status)) {
            log.info("게임 인터럽트 감지, 프레임 처리 중단: sessionId={}", sessionId);
            // 프론트에 중단 알림
            sendGameInterruptNotification(sessionId);
            return; // 프레임 처리 중단
        }

        GameState gameState = getGameState(sessionId);
        double currentPlayTime = request.getCurrentPlayTime();

        // --- ▼ (수정) AI 서버 호출 없이, 무조건 PERFECT(3)로 판정 ▼ ---
        int judgment = JUDGMENT_PERFECT;

        // 1. 판정 결과를 프론트엔드에 WebSocket으로 발송
        sendFeedback(sessionId, judgment, currentPlayTime);

        // 2. 판정 결과를 Redis의 GameState에 기록
        recordJudgment(sessionId, judgment, currentPlayTime);

        // TODO: findCorrectActionCodeForCurrentTime 메소드 구현 필요
//        int correctActionCode = findCorrectActionCodeForCurrentTime(gameState.getSongId(), currentPlayTime);

        // webClient.post()
        //         .uri("http://motion-server:8000/analyze_single_action")
        //         .bodyValue(Map.of("frameData", request.getFrameData()))
        //         .retrieve()
        //         .bodyToMono(AiResponse.class)
        //         .subscribe(
        //             aiResponse -> {
        //                 int userActionCode = aiResponse.getActionCode();
        //                 int judgment = determineJudgment(correctActionCode, userActionCode); // 1, 2, 3 점수 판정
        //
        //                 // 판정 결과를 프론트엔드에 WebSocket으로 발송
        //                 sendFeedback(sessionId, judgment, currentPlayTime);
        //
        //                 // TODO: 판정 결과를 Redis의 GameState에 기록
        //                 // recordJudgment(sessionId, verse, judgment);
        //             },
        //             error -> {
        //                 log.error("AI 서버 호출 실패 (세션 {}): {}", sessionId, error.getMessage());
        //                 sendFeedback(sessionId, 1, currentPlayTime); // 1점(BAD)으로 실패 피드백 전송
        //             }
        //         );
    }

    /**
     * 판정 결과를 Redis에 기록하는 헬퍼 메소드
     */
    private void recordJudgment(String sessionId, int judgment, double currentPlayTime) {
        GameState currentState = getGameState(sessionId);

        // TODO: currentPlayTime과 SongBeat 정보를 이용해 현재가 1절인지 2절인지 판단하는 로직 필요
        //       (우선은 임시로, 1절에만 점수를 기록하도록 단순화)
        boolean isVerse1 = true; // 임시

        if (isVerse1) {
            currentState.getVerse1Judgments().add(judgment);
        } else {
            currentState.getVerse2Judgments().add(judgment);
        }

        saveGameState(sessionId, currentState);
        log.trace("판정 기록 완료: sessionId={}, judgment={}", sessionId, judgment);
    }


    /**
     * 3. 1절 종료 시, 레벨 결정 결과를 WebSocket으로 발송하는 메소드
     */
    public void decideAndSendNextLevel(String sessionId) {
        GameState gameState = getGameState(sessionId);

        // GameState에 기록된 1절 판정 결과를 바탕으로 평균 점수 계산
        double averageScore = calculateScoreFromJudgments(gameState.getVerse1Judgments());
        int nextLevel = determineLevel(averageScore);

        // TODO: 결정된 레벨에 맞는 2절 시범 영상 URL 가져오기
        String characterVideoUrl = "https://example.com/video_v2_level" + nextLevel + ".mp4"; // 임시 URL

        gameState.setNextLevel(nextLevel);
        saveGameState(sessionId, gameState);

        String destination = "/topic/game/" + sessionId;
        LevelDecisionData levelData = new LevelDecisionData(nextLevel, characterVideoUrl);
        GameWebSocketMessage<LevelDecisionData> message = new GameWebSocketMessage<>("LEVEL_DECISION", levelData);
        messagingTemplate.convertAndSend(destination, message);

        log.info("세션 {}의 다음 레벨 결정: {}, 평균 점수: {}", sessionId, nextLevel, averageScore);
    }


    /**
     * 4. 게임 종료 및 결과 저장
     */
    @Transactional
    public void endGame(String sessionId) {
        GameState finalGameState = getGameState(sessionId);
        if (finalGameState == null) {
            log.warn("이미 처리되었거나 존재하지 않는 세션 ID로 종료 요청: {}", sessionId);
            return;
        }

        // 1, 2절 판정 결과를 바탕으로 최종 점수 계산
        double verse1Avg = calculateScoreFromJudgments(finalGameState.getVerse1Judgments());
        double verse2Avg = calculateScoreFromJudgments(finalGameState.getVerse2Judgments());

        // MongoDB: 상세 데이터 저장
        GameDetail.Statistics verse1Stats = calculateStatistics(finalGameState.getVerse1Judgments());
        GameDetail.Statistics verse2Stats = calculateStatistics(finalGameState.getVerse2Judgments());

        GameDetail gameDetail = GameDetail.builder()
                .sessionId(sessionId)
                // TODO: 실제 Movement 데이터는 프레임 처리 시 수집 필요
                .verse1Stats(verse1Stats)
                .verse2Stats(verse2Stats)
                .build();
        gameDetailRepository.save(gameDetail);

        // MySQL: 게임 결과 업데이트
        GameResult gameResult = gameResultRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND));

        gameResult.setVerse1AvgScore(verse1Avg);
        gameResult.setVerse2AvgScore(verse2Avg);
        gameResult.setFinalLevel(finalGameState.getNextLevel());
        gameResult.complete(); // 상태 = COMPLETED, endTime 설정

        gameResultRepository.save(gameResult);
        log.info("세션 {}의 게임 결과 저장 완료. 1절 점수: {}, 2절 점수: {}", sessionId, verse1Avg, verse2Avg);

        // Redis: 상태 정리
        gameStateRedisTemplate.delete(sessionId);
        sessionStateService.clearSessionStatus(sessionId);
        sessionStateService.clearActivity(finalGameState.getUserId());
        log.info("세션 {}의 Redis 데이터 삭제 완료.", sessionId);
    }

    /**
     * 게임 인터럽트 처리 (외부 호출용)
     */
    @Transactional
    public void interruptGame(String sessionId, String reason) {
        GameState finalGameState = getGameState(sessionId);
        if (finalGameState == null) {
            log.warn("존재하지 않는 세션 ID로 인터럽트 요청: {}", sessionId);
            return;
        }

        // 1, 2절 판정 결과를 바탕으로 점수 계산
        double verse1Avg = calculateScoreFromJudgments(finalGameState.getVerse1Judgments());
        double verse2Avg = calculateScoreFromJudgments(finalGameState.getVerse2Judgments());

        // MongoDB: 상세 데이터 저장 (진행된 부분까지)
        GameDetail.Statistics verse1Stats = calculateStatistics(finalGameState.getVerse1Judgments());
        GameDetail.Statistics verse2Stats = calculateStatistics(finalGameState.getVerse2Judgments());

        GameDetail gameDetail = GameDetail.builder()
                .sessionId(sessionId)
                .verse1Stats(verse1Stats)
                .verse2Stats(verse2Stats)
                .build();
        gameDetailRepository.save(gameDetail);

        // MySQL: 게임 결과 업데이트 (중단 처리)
        GameResult gameResult = gameResultRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND));

        gameResult.setVerse1AvgScore(verse1Avg);
        gameResult.setVerse2AvgScore(verse2Avg);
        gameResult.interrupt(reason); // 상태 = INTERRUPTED, endTime, interruptReason 설정

        gameResultRepository.save(gameResult);
        log.info("세션 {}의 게임 중단 처리 완료. 사유: {}", sessionId, reason);

        // Redis: 상태 정리
        gameStateRedisTemplate.delete(sessionId);
        sessionStateService.clearSessionStatus(sessionId);
        sessionStateService.clearActivity(finalGameState.getUserId());

        // WebSocket: 프론트에 중단 알림
        sendGameInterruptNotification(sessionId);
    }

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

    // --- Helper 메소드들 ---
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

    private GameState getGameState(String sessionId) {
        GameState gameState = gameStateRedisTemplate.opsForValue().get(sessionId);
        if (gameState == null) {
            throw new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND);
        }
        return gameState;
    }

    private void saveGameState(String sessionId, GameState gameState) {
        gameStateRedisTemplate.opsForValue().set(sessionId, gameState, Duration.ofMinutes(SESSION_TIMEOUT_MINUTES));
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