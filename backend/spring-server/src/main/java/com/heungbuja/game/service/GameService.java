package com.heungbuja.game.service;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.game.domain.GameDetail;
import com.heungbuja.game.domain.SpringServerPerformance;
import com.heungbuja.game.dto.*;
import com.heungbuja.game.entity.GameResult;
import com.heungbuja.game.entity.ScoreByAction;
import com.heungbuja.game.enums.GameSessionStatus;
import com.heungbuja.game.repository.mongo.GameDetailRepository;
import com.heungbuja.game.repository.mongo.SpringServerPerformanceRepository;
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
import com.heungbuja.game.repository.jpa.ScoreByActionRepository;
import com.heungbuja.s3.service.MediaUrlService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
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

    // --- 동작 코드와 이름을 매핑하는 캐시 ---
    private final Map<Integer, String> actionCodeToNameMap = new HashMap<>();

    // --- 상수 정의 ---
    /** Redis 세션 만료 시간 (분) */
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    private static final int JUDGMENT_PERFECT = 3;
    private static final double JUDGMENT_BUFFER_SECONDS = 0.2; // 앞뒤로 0.2초의 여유 시간

    // --- Redis Key 접두사 상수 ---
    private static final String GAME_STATE_KEY_PREFIX = "game_state:";
    private static final String GAME_SESSION_KEY_PREFIX = "game_session:";

    private final ScoreByActionRepository scoreByActionRepository;
    // --- AI 서버 응답 시간 통계 ---

    private static class AiResponseStats {
        private final List<Long> responseTimes = new ArrayList<>();
        private long lastReportTime = System.currentTimeMillis();
        private final long REPORT_INTERVAL_MS = 60000; // 60초마다 리포트
        private static GameService gameServiceInstance; // MongoDB 저장용

        public synchronized void record(long responseTimeMs) {
            responseTimes.add(responseTimeMs);
            maybeReport();
        }

        private void maybeReport() {
            long now = System.currentTimeMillis();
            if (now - lastReportTime >= REPORT_INTERVAL_MS && !responseTimes.isEmpty()) {
                report();
                reset();
            }
        }

        private void report() {
            if (responseTimes.isEmpty()) return;

            double avg = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long min = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
            long max = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

            log.info("================================================================================");
            log.info("📊 AI Server Response Time Statistics (Last 60s)");
            log.info("Total Requests: {}", responseTimes.size());
            log.info("  - Average: {}ms", String.format("%.2f", avg));
            log.info("  - Min: {}ms", min);
            log.info("  - Max: {}ms", max);
            log.info("================================================================================");

            // MongoDB에 저장
            if (gameServiceInstance != null) {
                try {
                    SpringServerPerformance perf = SpringServerPerformance.builder()
                            .timestamp(LocalDateTime.now())
                            .intervalSeconds(60)
                            .totalRequests(responseTimes.size())
                            .averageResponseTimeMs(avg)
                            .minResponseTimeMs(min)
                            .maxResponseTimeMs(max)
                            .build();
                    gameServiceInstance.springServerPerformanceRepository.save(perf);
                    log.info("✅ 성능 통계를 MongoDB에 저장했습니다.");
                } catch (Exception e) {
                    log.error("❌ MongoDB 저장 실패: {}", e.getMessage());
                }
            }
        }

        private void reset() {
            responseTimes.clear();
            lastReportTime = System.currentTimeMillis();
        }

        public static void setGameServiceInstance(GameService instance) {
            gameServiceInstance = instance;
        }
    }

    private static final AiResponseStats aiResponseStats = new AiResponseStats();

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
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionStateService sessionStateService;
    private final ChoreographyPatternRepository choreographyPatternRepository;
    private final ActionRepository actionRepository;
    private final MediaUrlService mediaUrlService;
    private final SpringServerPerformanceRepository springServerPerformanceRepository;
    private final com.heungbuja.game.repository.mongo.MotionInferenceLogRepository motionInferenceLogRepository;

    @Qualifier("aiWebClient") // 여러 WebClient Bean 중 aiWebClient를 특정
    private final WebClient aiWebClient;

    @PostConstruct
    public void init() {
        // AI 응답 통계를 위해 GameService 인스턴스 설정
        AiResponseStats.setGameServiceInstance(this);

        // --- 서버 시작 시 Action 정보를 캐시에 저장 ---
        actionRepository.findAll().forEach(action ->
                actionCodeToNameMap.put(action.getActionCode(), action.getName())
        );
        // --- ▲ ---------------------------------------------------- ▲ ---
    }

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

        // 섹션별 패턴 시퀀스 생성 (섹션 전체 길이만큼)
        GameStartResponse.SectionPatterns sectionPatterns = createSectionPatterns(songBeat, choreography);

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
        Map<String, String> videoUrls = generateVideoUrls(choreography);

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
                .sectionPatterns(sectionPatterns)
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
                .sectionPatterns(sectionPatterns)
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

        // ⭐ 패턴 시퀀스 배열을 순회하며 각 패턴의 시퀀스를 가져옴
        List<List<Integer>> patternSequenceList = new ArrayList<>();
        for (String patternId : verseInfo.getPatternSequence()) {
            List<Integer> patternSeq = findPatternSequenceById(patternData, patternId);
            patternSequenceList.add(patternSeq);
        }

        return generateTimelineForSection(beatNumToTimeMap, section, patternSequenceList, verseInfo.getEachRepeat());
    }

    /**
     * (신규) 2절과 같이 레벨별 패턴을 가진 절의 타임라인을 생성합니다.
     */
    private List<ActionTimelineEvent> createVerseTimelineForLevel(
            SongBeat songBeat, SongChoreography choreography, ChoreographyPattern patternData,
            Map<Integer, Double> beatNumToTimeMap, String sectionLabel,
            SongChoreography.VerseLevelPatternInfo levelInfo) {

        SongBeat.Section section = findSectionByLabel(songBeat, sectionLabel);

        // ⭐ 패턴 시퀀스 배열을 순회하며 각 패턴의 시퀀스를 가져옴
        List<List<Integer>> patternSequenceList = new ArrayList<>();
        for (String patternId : levelInfo.getPatternSequence()) {
            List<Integer> patternSeq = findPatternSequenceById(patternData, patternId);
            patternSequenceList.add(patternSeq);
        }

        return generateTimelineForSection(beatNumToTimeMap, section, patternSequenceList, levelInfo.getEachRepeat());
    }

    /**
     * (신규) 특정 구간과 동작 시퀀스를 받아 실제 타임라인 리스트를 생성하는 공통 메소드
     * 여러 패턴을 병합하여 타임라인을 생성합니다
     */
    private List<ActionTimelineEvent> generateTimelineForSection(
            Map<Integer, Double> beatNumToTimeMap,
            SongBeat.Section section,
            List<List<Integer>> patternSequenceList,
            int eachRepeat) {

        List<ActionTimelineEvent> timeline = new ArrayList<>();
        Map<Integer, String> actionCodeToNameMap = actionRepository.findAll().stream()
                .collect(Collectors.toMap(Action::getActionCode, Action::getName));

        int startBeat = section.getStartBeat();
        int endBeat = section.getEndBeat();

        // ⭐ 1. 패턴 배열을 하나의 큰 패턴으로 병합 (패턴 전체를 eachRepeat번 반복)
        List<Integer> mergedPattern = new ArrayList<>();
        for (int i = 0; i < eachRepeat; i++) {
            for (List<Integer> pattern : patternSequenceList) {
                mergedPattern.addAll(pattern);
            }
        }

        int mergedPatternLength = mergedPattern.size();

        // ⭐ 2. Modulo로 섹션 전체를 채움 (기존 로직 유지)
        for (int currentBeatIndex = startBeat; currentBeatIndex <= endBeat; currentBeatIndex++) {
            int beatWithinSection = currentBeatIndex - startBeat;
            int patternIndex = beatWithinSection % mergedPatternLength;
            int actionCode = mergedPattern.get(patternIndex);

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
                        s -> barStartTimes.getOrDefault(s.getStartBeat(), 0.0)
                ));
    }

    /**
     * 섹션별 패턴 시퀀스 생성 (섹션 전체 길이만큼 패턴 반복)
     * 예: verse1이 80비트이고 patternSequence=["P1","P2"], eachRepeat=2라면
     *     ["P1","P1","P2","P2", "P1","P1","P2","P2", ...] (80개)
     */
    private GameStartResponse.SectionPatterns createSectionPatterns(SongBeat songBeat, SongChoreography choreography) {
        SongChoreography.Version version = choreography.getVersions().get(0);

        // 1절 패턴 시퀀스 생성 (섹션 전체 길이만큼)
        List<String> verse1Patterns = createFullSectionPatternSequence(
                songBeat,
                version.getVerse1().getPatternSequence(),
                version.getVerse1().getEachRepeat(),
                "verse1"
        );

        // 2절 레벨별 패턴 시퀀스 생성
        Map<Integer, List<String>> verse2PatternsMap = new HashMap<>();
        for (SongChoreography.VerseLevelPatternInfo levelInfo : version.getVerse2()) {
            List<String> levelPatterns = createFullSectionPatternSequence(
                    songBeat,
                    levelInfo.getPatternSequence(),
                    levelInfo.getEachRepeat(),
                    "verse2"
            );
            verse2PatternsMap.put(levelInfo.getLevel(), levelPatterns);
        }

        // Verse2Patterns 객체 생성
        GameStartResponse.Verse2Patterns verse2Patterns = GameStartResponse.Verse2Patterns.builder()
                .level1(verse2PatternsMap.get(1))
                .level2(verse2PatternsMap.get(2))
                .level3(verse2PatternsMap.get(3))
                .build();

        return GameStartResponse.SectionPatterns.builder()
                .verse1(verse1Patterns)
                .verse2(verse2Patterns)
                .build();
    }

    /**
     * 섹션 전체 길이만큼 패턴을 반복하여 배열 생성
     */
    private List<String> createFullSectionPatternSequence(
            SongBeat songBeat,
            List<String> patternSequence,
            int eachRepeat,
            String sectionLabel) {

        // 1. 기본 패턴 시퀀스 생성 (패턴 전체를 eachRepeat번 반복)
        List<String> mergedPattern = new ArrayList<>();
        for (int i = 0; i < eachRepeat; i++) {
            for (String patternId : patternSequence) {
                mergedPattern.add(patternId);
            }
        }

        // 2. 섹션의 비트 범위 가져오기
        SongBeat.Section section = findSectionByLabel(songBeat, sectionLabel);
        int sectionBeatCount = section.getEndBeat() - section.getStartBeat() + 1;

        // 3. 섹션 전체 길이만큼 패턴 반복
        List<String> fullPatternSequence = new ArrayList<>();
        int mergedPatternLength = mergedPattern.size();

        for (int i = 0; i < sectionBeatCount; i++) {
            int patternIndex = i % mergedPatternLength;
            fullPatternSequence.add(mergedPattern.get(patternIndex));
        }

        return fullPatternSequence;
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

        gameSession.setLastFrameReceivedTime(Instant.now().toEpochMilli());

        List<ActionTimelineEvent> timeline = getCurrentTimeline(gameState, gameSession);
        int nextActionIndex = gameSession.getNextActionIndex();

        if (nextActionIndex >= timeline.size()) {
            saveGameSession(sessionId, gameSession);
            return;
        }

        ActionTimelineEvent currentAction = timeline.get(nextActionIndex);
        double actionTime = currentAction.getTime();

        // 프레임 수집
        if (currentPlayTime >= actionTime - JUDGMENT_BUFFER_SECONDS &&
                currentPlayTime <= actionTime + JUDGMENT_BUFFER_SECONDS) {
            gameSession.getFrameBuffer().put(currentPlayTime, request.getFrameData());
        }

        // 판정 트리거
        if (currentPlayTime > actionTime + JUDGMENT_BUFFER_SECONDS) {
            if (!gameSession.getFrameBuffer().isEmpty()) {

                // --- ▼ (핵심 수정) 2번에 1번만 AI 서버를 호출하도록 변경 ---
                if (gameSession.getJudgmentCount() % 1 == 0) {
                    List<String> frames = new ArrayList<>(gameSession.getFrameBuffer().values());
                    callAiServerForJudgment(sessionId, gameSession, currentAction, frames);
                    log.info(" > AI 서버 요청 실행 (카운트: {})", gameSession.getJudgmentCount());
                } else {
                    log.info(" > AI 서버 요청 건너뛰기 (카운트: {})", gameSession.getJudgmentCount());
                }
                // 카운터 증가
                gameSession.setJudgmentCount(gameSession.getJudgmentCount() + 1);
                // --- ▲ -------------------------------------------------- ▲ ---

            }

            gameSession.setNextActionIndex(nextActionIndex + 1);
            gameSession.getFrameBuffer().clear();

            if (gameSession.getNextLevel() != null && gameSession.getNextActionIndex() >= timeline.size()) {
                log.info("세션 {}의 2절 모든 동작 판정 완료. 프론트엔드의 /api/game/end 호출을 대기합니다.", sessionId);
            }
        }
        saveGameSession(sessionId, gameSession);
    }

    /**
     * 1초마다 실행되는 게임 세션 감시자
     * 1. 인터럽트 요청 확인 (수정됨)
     * 2. 프레임 수신 타임아웃 확인
     */
    @Transactional
    @Scheduled(fixedRate = 1000)
    public void checkGameSessionTimeout() {
        Set<String> sessionKeys = gameSessionRedisTemplate.keys(GAME_SESSION_KEY_PREFIX + "*");
        if (sessionKeys == null || sessionKeys.isEmpty()) {
            return;
        }

        long now = Instant.now().toEpochMilli();

        for (String key : sessionKeys) {
            GameSession session = gameSessionRedisTemplate.opsForValue().get(key);
            // --- (수정) session이 null일 경우를 대비하여 Null-safe하게 처리 ---
            if (session == null) {
                log.warn("Redis에서 GameSession을 찾았으나 (key: {}), 실제 객체가 null입니다. 데이터 손상 가능성이 있습니다.", key);
                continue;
            }
            if (session.isProcessing()) {
                continue;
            }

            String sessionId = session.getSessionId();

            // --- ▼ (핵심 수정) "EMERGENCY_INTERRUPT" 상태를 확인하도록 변경 ---
            String status = sessionStateService.getSessionStatus(sessionId);
//            log.info("------ !!!!! 세션ID : {}, 현재 상태 : {}", sessionId, status);
            if ("EMERGENCY_INTERRUPT".equals(status)) {

                log.info("-------- !!!!!! 세션 {}에 대한 인터럽트 요청 감지 (상태: {}). 게임 중단 처리를 시작합니다.", sessionId, status);

                // interruptGame 메소드가 내부적으로 중복 실행 방지(락) 처리를 하므로
                // 여기서는 바로 호출하기만 하면 됩니다.
                interruptGame(sessionId, "EMERGENCY");

                // interruptGame이 모든 정리를 담당하므로, 여기서는 더 이상 할 일이 없습니다.
                // 다음 세션 검사를 위해 continue 합니다.
                continue;
            }
            // --- ▲ ----------------------------------------------------------- ▲ ---


            // 기존 타임아웃 검사 로직
            if (session.getLastFrameReceivedTime() > 0 && now - session.getLastFrameReceivedTime() > 1000) {
                if (session.getNextLevel() == null) {
                    log.info("세션 {}의 1절 종료 감지. 레벨 결정을 시작합니다.", sessionId);
                    session.setProcessing(true);
                    saveGameSession(sessionId, session);
                    decideAndSendNextLevel(sessionId);
                }
            }
        }
    }

    /**
     * 모인 프레임 묶음을 AI 서버로 보내고, 결과를 처리하는 메소드 (비동기)
     */
    private void callAiServerForJudgment(String sessionId, GameSession gameSession, ActionTimelineEvent action, List<String> frames) {
        long startTime = System.currentTimeMillis();
        log.info("세션 {}의 동작 '{}'에 대한 AI 분석 요청 전송. (프레임 {}개)", sessionId, action.getActionName(), frames.size());

        AiAnalyzeRequest requestBody = AiAnalyzeRequest.builder()
                .actionCode(action.getActionCode())
                .actionName(action.getActionName())
                .frameCount(frames.size())
                .frames(frames)
                .build();

        aiWebClient.post()
                .uri("/api/ai/analyze")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(AiJudgmentResponse.class)
                .subscribe(
                        aiResponse -> { // 성공 시
                            long responseTime = System.currentTimeMillis() - startTime;
                            aiResponseStats.record(responseTime);

                            int actionCode = aiResponse.getActionCode();
                            int judgment = aiResponse.getJudgment();
                            log.info("⏱️ AI 분석 결과 수신 (세션 {}): actionCode={}, judgment={}, 목표확률={}, 신뢰도={} (응답시간: {}ms)",
                                    sessionId, actionCode, judgment,
                                    String.format("%.1f%%", (aiResponse.getTargetProbability() != null ? aiResponse.getTargetProbability() : 0) * 100),
                                    String.format("%.1f%%", aiResponse.getConfidence() * 100),
                                    responseTime);

                            // ========================================================================
                            // MongoDB에 추론 상세 로그 저장 (정확도 분석용)
                            // ========================================================================
                            saveMotionInferenceLog(sessionId, gameSession.getUserId(), action, aiResponse, responseTime, frames.size(), true, null);

                            handleJudgmentResult(sessionId, actionCode, judgment, action.getTime());
                        },
                        error -> { // 실패 시
                            long responseTime = System.currentTimeMillis() - startTime;
                            // AI 서버 통신 중 에러가 발생하면, 서버가 중단되지 않고
                            // 판정 점수를 1점으로 처리하여 게임을 계속 진행합니다.
                            log.error("AI 서버 호출 중 오류 발생 (세션 ID: {}). 기본 점수(1점)으로 처리합니다. (소요시간: {}ms)", sessionId, responseTime, error);

                            // ========================================================================
                            // AI 서버 에러 처리 (0점)
                            // ========================================================================
                            // 주요 실패 원인:
                            // 1. 유효한 프레임 부족 (< 5개) → 사람이 화면에 안 보임 또는 움직이지 않음
                            // 2. Mediapipe 감지 실패 → 카메라 각도/조명 문제
                            // 3. 네트워크 타임아웃 또는 서버 장애
                            //
                            // → 모든 실패는 0점 처리 (공정한 채점)
                            // ========================================================================

                            // MongoDB에 실패 로그 저장
                            saveMotionInferenceLog(sessionId, gameSession.getUserId(), action, null, responseTime, frames.size(), false, error.getMessage());

                            handleJudgmentResult(sessionId, action.getActionCode(), 0, action.getTime());
                        }
                );
    }


    /**
     * AI 판정 결과를 받아 후속 처리를 하는 메소드
     * (주의: 이 메소드는 비동기 콜백에서 호출되므로, 여기서 가져오는 gameSession은 최신이 아닐 수 있음)
     */
    private void handleJudgmentResult(String sessionId, int actionCode, int judgment, double actionTime) {
        sendFeedback(sessionId, judgment, actionTime);

        GameSession latestGameSession = getGameSession(sessionId);
        if (latestGameSession != null) {
            // --- ▼ (핵심 수정) actionCode도 함께 전달합니다. ---
            recordJudgment(actionCode, judgment, latestGameSession);
            // --- ▲ ------------------------------------------- ▲ ---
            saveGameSession(sessionId, latestGameSession);
        } else {
            log.warn("AI 응답 처리 시점(세션 {})에 Redis에서 GameSession을 찾을 수 없습니다.", sessionId);
        }
    }


    /**
     * 판정 결과를 Redis('GameSession')에 기록하는 헬퍼 메소드
     */
    private void recordJudgment(int actionCode, int judgment, GameSession currentSession) {
        int verse = (currentSession.getNextLevel() == null) ? 1 : 2;

        // JudgmentResult 객체 생성
        GameSession.JudgmentResult result = new GameSession.JudgmentResult(actionCode, judgment);

        if (verse == 1) {
            currentSession.getVerse1Judgments().add(result);
        } else {
            currentSession.getVerse2Judgments().add(result);
        }
        log.trace("판정 기록 준비: sessionId={}, actionCode={}, judgment={}, verse={}",
                currentSession.getSessionId(), actionCode, judgment, verse);
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
     * 4. 게임 종료 및 결과 저장 (API 호출용으로 재설계)
     * sessionId를 받아 점수를 계산하고, DB에 저장한 뒤, 최종 점수와 평가 문구를 반환합니다.
     */
    @Transactional
    public GameEndResponse endGame(String sessionId) {
        String sessionKey = GAME_SESSION_KEY_PREFIX + sessionId;
        GameSession finalSession = gameSessionRedisTemplate.opsForValue().get(sessionKey);

        Double verse1Avg;
        Double verse2Avg;
        Map<Integer, Double> avgScoresByActionCode; // actionCode를 Key로 하는 점수 맵

        if (finalSession == null) {
            // Redis에 세션이 없는 경우: DB에서 기존 기록을 조회하여 응답 구성
            GameResult existingResult = gameResultRepository.findBySessionIdWithScores(sessionId)
                    .orElseThrow(() -> new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND, "Redis와 DB 모두에서 세션을 찾을 수 없습니다: " + sessionId));

            log.warn("Redis에서 세션 {}을 찾을 수 없었으나, DB 기록을 바탕으로 결과를 반환합니다.", sessionId);
            verse1Avg = existingResult.getVerse1AvgScore();
            verse2Avg = existingResult.getVerse2AvgScore();

            // DB에 저장된 ScoreByAction 리스트를 Map으로 변환
            avgScoresByActionCode = existingResult.getScoresByAction().stream()
                    .collect(Collectors.toMap(ScoreByAction::getActionCode, ScoreByAction::getAverageScore));

        } else {
            // Redis에 세션이 있는 경우: Redis 데이터를 기반으로 모든 정보 계산 및 저장
            verse1Avg = calculateScoreFromJudgments(finalSession.getVerse1Judgments());
            verse2Avg = null;
            if (finalSession.getNextLevel() != null || (finalSession.getVerse2Judgments() != null && !finalSession.getVerse2Judgments().isEmpty())) {
                verse2Avg = calculateScoreFromJudgments(finalSession.getVerse2Judgments());
            }

            // MongoDB 저장
            GameDetail.Statistics verse1Stats = calculateStatistics(finalSession.getVerse1Judgments());
            GameDetail.Statistics verse2Stats = calculateStatistics(finalSession.getVerse2Judgments());
            GameDetail gameDetail = GameDetail.builder().sessionId(sessionId).verse1Stats(verse1Stats).verse2Stats(verse2Stats).build();
            gameDetailRepository.save(gameDetail);

            // MySQL 저장
            GameResult gameResult = gameResultRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND));

            gameResult.setVerse1AvgScore(verse1Avg);
            gameResult.setVerse2AvgScore(verse2Avg);
            gameResult.setFinalLevel(finalSession.getNextLevel());
            gameResult.complete();

            // 동작별 점수 계산 및 GameResult에 추가 후, 계산된 Map을 반환받음
            avgScoresByActionCode = calculateAndSaveScoresByAction(finalSession, gameResult);

            gameResultRepository.save(gameResult);
            log.info("세션 {}의 게임 결과 저장 완료. 1절 점수: {}, 2절 점수: {}", sessionId, verse1Avg, verse2Avg);

            // Redis 데이터 정리
            gameSessionRedisTemplate.delete(sessionKey);
            gameStateRedisTemplate.delete(GAME_STATE_KEY_PREFIX + sessionId);
            sessionStateService.clearSessionStatus(sessionId);
            if(finalSession.getUserId() != null) {
                sessionStateService.clearActivity(finalSession.getUserId());
            }
            log.info("세션 {}의 Redis 데이터 삭제 완료.", sessionId);
        }

        // --- ▼ (핵심 수정) 최종 응답을 생성하기 전에 actionCode 맵을 actionName 맵으로 변환 ---
        Map<String, Double> scoresByActionName = avgScoresByActionCode.entrySet().stream()
                .collect(Collectors.toMap(
                        // entry의 key(actionCode)를 캐시에서 찾아 actionName으로 변환
                        entry -> actionCodeToNameMap.getOrDefault(entry.getKey(), "알 수 없는 동작 #" + entry.getKey()),
                        Map.Entry::getValue
                ));
        // --- ▲ ------------------------------------------------------------------------- ▲ ---

        // 최종 점수와 메시지 계산하여 반환
        double finalScore = calculateFinalScore(verse1Avg, verse2Avg);
        String message = getResultMessage(finalScore);

        return GameEndResponse.builder()
                .finalScore(finalScore)
                .message(message)
                .scoresByAction(scoresByActionName) // <-- 변환된 Map을 응답에 추가
                .build();
    }

    /**
     * 게임 인터럽트 처리 (외부 호출용 및 스케줄러 호출용)
     * sessionId와 중단 사유를 받아 게임을 중단 상태로 종료합니다.
     */
    @Transactional
    public void interruptGame(String sessionId, String reason) {
        // 중복 실행 방지를 위해 락을 시도합니다. (이 메소드는 외부에서 직접 호출될 수 있으므로 유지)
        if (!sessionStateService.trySetInterrupt(sessionId, reason)) {
            log.warn("세션 {}에 대한 인터럽트가 이미 처리 중이므로 건너뜁니다.", sessionId);
            return;
        }

        GameSession finalSession = getGameSession(sessionId);
        if (finalSession == null) {
            log.warn("존재하지 않거나 이미 처리된 세션 ID로 인터럽트 요청: {}", sessionId);
            GameResult gameResult = gameResultRepository.findBySessionId(sessionId).orElse(null);
            if (gameResult != null && gameResult.getStatus() == GameSessionStatus.IN_PROGRESS) {
                gameResult.interrupt(reason);
                gameResultRepository.save(gameResult);
                log.info("DB에만 남아있던 세션 {}의 게임을 중단 처리했습니다.", sessionId);
            }
            sessionStateService.releaseInterruptLock(sessionId); // 락 해제
            return;
        }

        // --- 1. 절별 평균 점수 계산 (기존 로직) ---
        Double verse1Avg = calculateScoreFromJudgments(finalSession.getVerse1Judgments());
        Double verse2Avg = calculateScoreFromJudgments(finalSession.getVerse2Judgments());

        // --- 2. MongoDB 상세 데이터 저장 (기존 로직) ---
        GameDetail.Statistics verse1Stats = calculateStatistics(finalSession.getVerse1Judgments());
        GameDetail.Statistics verse2Stats = calculateStatistics(finalSession.getVerse2Judgments());
        GameDetail gameDetail = GameDetail.builder()
                .sessionId(sessionId)
                .verse1Stats(verse1Stats)
                .verse2Stats(verse2Stats)
                .build();
        gameDetailRepository.save(gameDetail);

        // --- 3. MySQL 게임 결과 엔티티 조회 (기존 로직) ---
        GameResult gameResult = gameResultRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_SESSION_NOT_FOUND));

        // --- 4. 절별 평균 점수 및 상태 설정 (기존 로직) ---
        gameResult.setVerse1AvgScore(verse1Avg);
        gameResult.setVerse2AvgScore(verse2Avg);
        gameResult.interrupt(reason); // <-- 상태를 'INTERRUPTED'로 설정

        // --- 5. (핵심 추가) 동작별 점수 계산 및 GameResult에 추가 ---
        calculateAndSaveScoresByAction(finalSession, gameResult);
        // --- ▲ ---------------------------------------------- ▲ ---

        // --- 6. MySQL에 최종 결과 저장 ---
        gameResultRepository.save(gameResult);
        log.info("세션 {}의 게임 중단 처리 완료. 사유: {}", sessionId, reason);

        // --- 7. Redis 데이터 정리 (기존 로직) ---
        gameSessionRedisTemplate.delete(GAME_SESSION_KEY_PREFIX + sessionId);
        gameStateRedisTemplate.delete(GAME_STATE_KEY_PREFIX + sessionId);
        sessionStateService.clearSessionStatus(sessionId);
        if (finalSession.getUserId() != null) {
            sessionStateService.clearActivity(finalSession.getUserId());
        }

        // --- 8. 모든 처리가 끝난 후 락 해제 (기존 로직) ---
        sessionStateService.releaseInterruptLock(sessionId);

        // --- 9. WebSocket으로 프론트에 중단 알림 전송 (기존 로직) ---
        sendGameInterruptNotification(sessionId);

        log.info("세션 {}의 모든 인터럽트 처리 및 뒷정리 완료.", sessionId);
    }

    // ##########################################################
    //                      헬퍼 메서드
    // ##########################################################

    /**
     * (신규) 동작별 평균 점수를 계산하고 GameResult 엔티티에 추가하는 헬퍼 메소드
     */
    private Map<Integer, Double> calculateAndSaveScoresByAction(GameSession finalSession, GameResult gameResult) {
        List<GameSession.JudgmentResult> allJudgments = new ArrayList<>();
        if (finalSession.getVerse1Judgments() != null) {
            allJudgments.addAll(finalSession.getVerse1Judgments());
        }
        if (finalSession.getVerse2Judgments() != null) {
            allJudgments.addAll(finalSession.getVerse2Judgments());
        }

        if (allJudgments.isEmpty()) {
            return Collections.emptyMap(); // 판정 기록이 없으면 빈 Map 반환
        }

        Map<Integer, Double> avgScoresByAction = allJudgments.stream()
                .collect(Collectors.groupingBy(
                        GameSession.JudgmentResult::getActionCode,
                        Collectors.averagingDouble(r -> (double) r.getJudgment() / 3.0 * 100.0)
                ));

        log.info("동작별 평균 점수 계산 완료: {}", avgScoresByAction);

        gameResult.getScoresByAction().clear();
        avgScoresByAction.forEach((actionCode, avgScore) -> {
            ScoreByAction score = ScoreByAction.builder()
                    .gameResult(gameResult)
                    .actionCode(actionCode)
                    .averageScore(avgScore)
                    .build();
            gameResult.addScoreByAction(score);
        });

        return avgScoresByAction; // <-- (핵심 추가) 계산된 Map을 반환
    }


    /**
     * (수정) JudgmentResult 리스트를 받아 100점 만점 점수로 변환하는 메소드
     */
    private double calculateScoreFromJudgments(List<GameSession.JudgmentResult> judgments) {
        if (judgments == null || judgments.isEmpty()) {
            return 0.0;
        }
        // 각 판정 점수(1,2,3)를 100점 만점으로 환산
        return judgments.stream()
                .mapToDouble(j -> (double) j.getJudgment() / 3.0 * 100.0)
                .average()
                .orElse(0.0);
    }

    /**
     * (수정) JudgmentResult 리스트를 받아 통계를 계산하는 메소드
     */
    private GameDetail.Statistics calculateStatistics(List<GameSession.JudgmentResult> judgments) {
        if (judgments == null || judgments.isEmpty()) {
            return GameDetail.Statistics.builder().build(); // 기본값 반환
        }

        List<Integer> judgmentScores = judgments.stream()
                .map(GameSession.JudgmentResult::getJudgment)
                .collect(Collectors.toList());

        int perfectCount = (int) judgmentScores.stream().filter(j -> j == 3).count();
        int goodCount = (int) judgmentScores.stream().filter(j -> j == 2).count();
        int badCount = (int) judgmentScores.stream().filter(j -> j == 1).count();

        return GameDetail.Statistics.builder()
                .totalMovements(judgmentScores.size())
                .correctMovements(perfectCount + goodCount)
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
        String key = GAME_SESSION_KEY_PREFIX + sessionId;
        // 이제 이 메소드는 순수하게 조회만 담당. 없으면 null 반환.
        return gameSessionRedisTemplate.opsForValue().get(key);
    }

    private void saveGameSession(String sessionId, GameSession gameSession) {
        String key = GAME_SESSION_KEY_PREFIX + sessionId; // <-- (수정) 올바른 Key를 정의합니다.
        gameSessionRedisTemplate.opsForValue().set(key, gameSession, Duration.ofMinutes(SESSION_TIMEOUT_MINUTES)); // <-- (수정) 정의된 Key를 사용합니다.
    }


    private int determineLevel(double averageScore) {
        if (averageScore >= 80) return 3;
        if (averageScore >= 60) return 2;
        return 1;
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

    /**
     * 최종 점수를 계산하는 헬퍼 메소드
     * null이 아닌 값들의 평균을 계산합니다.
     */
    private double calculateFinalScore(Double verse1Score, Double verse2Score) {
        List<Double> scores = new ArrayList<>();
        if (verse1Score != null) {
            scores.add(verse1Score);
        }
        if (verse2Score != null) {
            scores.add(verse2Score);
        }

        if (scores.isEmpty()) {
            return 0.0;
        }

        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * 최종 점수에 따른 평가 문구를 반환하는 헬퍼 메소드
     */
    private String getResultMessage(double finalScore) {
        if (finalScore == 100) {
            return "완벽한 무대였습니다! 소름 돋았어요!";
        } else if (finalScore >= 90) {
            return "실력이 수준급이시네요!";
        } else if (finalScore >= 80) {
            return "체조교실 좀 다녀보신 솜씨네요!";
        } else if (finalScore >= 70) {
            return "멋져요! 다음 곡은 더 잘하실 수 있을 거예요!";
        } else {
            return "다음 기회에 더 멋진 무대 기대할게요!";
        }
    }

    /**
     * Motion AI 추론 결과를 MongoDB에 저장 (정확도 분석용)
     */
    private void saveMotionInferenceLog(
            String sessionId,
            Long userId,
            ActionTimelineEvent action,
            AiJudgmentResponse aiResponse,
            long totalResponseTimeMs,
            int totalFrameCount,
            boolean success,
            String errorMessage) {

        try {
            com.heungbuja.game.domain.MotionInferenceLog.MotionInferenceLogBuilder builder =
                    com.heungbuja.game.domain.MotionInferenceLog.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .targetActionCode(action.getActionCode())
                    .targetActionName(action.getActionName())
                    .totalFrameCount(totalFrameCount)
                    .totalResponseTimeMs(totalResponseTimeMs)
                    .success(success);

            if (aiResponse != null) {
                // 성공 케이스: AI 응답 데이터 저장
                builder.predictedActionCode(aiResponse.getActionCode())
                        .predictedActionName(aiResponse.getPredictedLabel())
                        .targetProbability(aiResponse.getTargetProbability())
                        .maxConfidence(aiResponse.getConfidence())
                        .judgment(aiResponse.getJudgment())
                        .validFrameCount(totalFrameCount)  // Motion 서버에서 필터링된 개수는 현재 전달 안 됨
                        .decodeTimeMs(aiResponse.getDecodeTimeMs())
                        .poseExtractionTimeMs(aiResponse.getPoseTimeMs())
                        .inferenceTimeMs(aiResponse.getInferenceTimeMs());
            } else {
                // 실패 케이스: 에러 정보만 저장
                builder.judgment(0)
                        .validFrameCount(0)
                        .errorMessage(errorMessage);
            }

            motionInferenceLogRepository.save(builder.build());

            log.debug("MongoDB에 추론 로그 저장 완료: sessionId={}, targetAction={}, success={}",
                    sessionId, action.getActionName(), success);

        } catch (Exception e) {
            // MongoDB 저장 실패해도 게임 진행에는 영향 없음
            log.error("MongoDB 추론 로그 저장 실패: {}", e.getMessage());
        }
    }

    // --- 비디오 URL 생성 (패턴 기반) ---

    /**
     * 비디오 URL 생성 (패턴 기반)
     */
    private Map<String, String> generateVideoUrls(SongChoreography choreography) {
        Map<String, String> videoUrls = new HashMap<>();

        SongChoreography.Version version = choreography.getVersions().get(0);

        // intro: 공통 튜토리얼
        String introS3Key = "video/break.mp4";
        videoUrls.put("intro", mediaUrlService.issueUrlByKey(introS3Key));

        // verse1: 첫 번째 패턴
        String verse1PatternId = version.getVerse1().getPatternSequence().get(0);
        String verse1S3Key = convertPatternIdToVideoUrl(verse1PatternId);
        videoUrls.put("verse1", mediaUrlService.issueUrlByKey(verse1S3Key));

        // verse2: 각 레벨의 첫 번째 패턴
        for (SongChoreography.VerseLevelPatternInfo levelInfo : version.getVerse2()) {
            String patternId = levelInfo.getPatternSequence().get(0);
            String s3Key = convertPatternIdToVideoUrl(patternId);
            String key = "verse2_level" + levelInfo.getLevel();
            videoUrls.put(key, mediaUrlService.issueUrlByKey(s3Key));
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

    // --- ▼ (테스트용 코드) AI 서버 연동을 테스트하기 위한 임시 메소드 ---
//    public Mono<AiJudgmentResponse> testAiServerConnection() {
//        log.info("AI 서버 연동 테스트를 시작합니다...");
//
//        // 1. AI 서버에 보낼 가짜(Mock) 데이터 생성
//        AiAnalyzeRequest mockRequest = AiAnalyzeRequest.builder()
//                .actionCode(99) // 테스트용 임의의 액션 코드
//                .actionName("테스트 동작")
//                .frames(List.of("dummy-base64-frame-1", "dummy-base64-frame-2"))
//                .build();
//
//        log.info(" > AI 서버로 전송할 요청 데이터: {}", mockRequest);
//
//        // 2. 실제 AI 서버 호출 로직 실행
//        return aiWebClient.post()
//                .uri("/api/ai/analyze")
//                .bodyValue(mockRequest)
//                .retrieve() // 응답을 받기 시작
//                .bodyToMono(AiJudgmentResponse.class) // 응답을 AiJudgmentResponse DTO로 변환
//                .doOnSuccess(response -> { // 성공 시 로그
//                    log.info(" > AI 서버로부터 성공적으로 응답을 받았습니다: judgment = {}", response.getJudgment());
//                })
//                .doOnError(error -> { // 실패 시 로그
//                    log.error(" > AI 서버 호출에 실패했습니다: {}", error.getMessage());
//                });
//    }
}