package com.heungbuja.game.service;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.game.dto.*;
import com.heungbuja.game.entity.GameResult;
import com.heungbuja.game.repository.GameResultRepository;
import com.heungbuja.game.state.GameState;
import com.heungbuja.s3.service.MediaUrlService;
import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.domain.SongChoreography;
import com.heungbuja.song.domain.SongLyrics;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.SongBeatRepository;
import com.heungbuja.song.repository.SongChoreographyRepository;
import com.heungbuja.song.repository.SongLyricsRepository;
import com.heungbuja.song.repository.SongRepository;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

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
//    private final MediaUrlService mediaUrlService;
    private final SimpMessagingTemplate messagingTemplate;
//    private final ScoringStrategyFactory scoringStrategyFactory;

    /**
     * 1. 게임 시작 로직
     */
    @Transactional(readOnly = true)
    public GameStartResponse startGame(GameStartRequest request) {
        // (참고) 실제 구현 시에는 이 메소드에 @AuthenticationPrincipal을 추가하여
        //        토큰에서 추출한 사용자 정보를 직접 받는 것이 더 좋습니다.
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!user.getIsActive()) {
            throw new CustomException(ErrorCode.USER_NOT_ACTIVE);
        }

        Song song = songRepository.findById(request.getSongId())
                .orElseThrow(() -> new CustomException(ErrorCode.SONG_NOT_FOUND));
        Long songId = request.getSongId();

        // 1-3. MongoDB에서 노래 비트 및 가사 데이터 조회
        SongBeat songBeat = songBeatRepository.findBySongId(songId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "비트 정보를 찾을 수 없습니다."));
        SongLyrics lyricsInfo = songLyricsRepository.findBySongId(songId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_METADATA_NOT_FOUND, "가사 정보를 찾을 수 없습니다."));

        // --- ▼ 데이터 가공 로직 시작 ▼ ---

        // 1. 각 마디(bar)의 시작 시간을 Map으로 미리 계산 (프론트 로직과 동일)
        Map<Integer, Double> barStartTimes = new HashMap<>();
        songBeat.getBeats().stream()
                .filter(beat -> beat.getBeat() == 1) // 각 마디의 첫 번째 비트만 필터링
                .forEach(beat -> barStartTimes.put(beat.getBar(), beat.getT()));

        // 2. 주요 섹션(intro, part1 등)의 시작 시간 찾기
        Map<String, Double> sectionStartTimes = songBeat.getSections().stream()
                .collect(Collectors.toMap(
                        SongBeat.Section::getLabel,
                        section -> barStartTimes.getOrDefault(section.getStartBar(), 0.0)
                ));

        // --- ▼ (핵심 수정) verse1cam, verse2cam 시간 계산 로직 추가 ▼ ---

        // A. 비트 번호(i)를 키로, 비트 시간(t)을 값으로 갖는 Map을 미리 생성 (시간 조회 성능 향상)
        Map<Integer, Double> beatNumToTimeMap = songBeat.getBeats().stream()
                .collect(Collectors.toMap(SongBeat.Beat::getI, SongBeat.Beat::getT));

        // B. verse1, verse2 섹션 객체를 찾습니다.
        SongBeat.Section verse1Section = findSectionByLabel(songBeat, "verse1");
        SongBeat.Section verse2Section = findSectionByLabel(songBeat, "verse2");

        // C. verse1cam의 시작/종료 시간 계산
        // 시작: 1절 시작 비트 + 16 비트
        int verse1CamStartBeat = verse1Section.getStartBeat() + 16;
        // 종료: 1절 시작 비트 + 16 비트 + (16 * 6) 비트
        int verse1CamEndBeat = verse1CamStartBeat + (16 * 6);

        SectionInfo.VerseInfo verse1CamInfo = SectionInfo.VerseInfo.builder()
                .startTime(beatNumToTimeMap.getOrDefault(verse1CamStartBeat, 0.0))
                .endTime(beatNumToTimeMap.getOrDefault(verse1CamEndBeat, 0.0))
                .build();

        // D. verse2cam의 시작/종료 시간 계산 (1절과 동일한 로직)
        int verse2CamStartBeat = verse2Section.getStartBeat() + 16;
        int verse2CamEndBeat = verse2CamStartBeat + (16 * 6);

        SectionInfo.VerseInfo verse2CamInfo = SectionInfo.VerseInfo.builder()
                .startTime(beatNumToTimeMap.getOrDefault(verse2CamStartBeat, 0.0))
                .endTime(beatNumToTimeMap.getOrDefault(verse2CamEndBeat, 0.0))
                .build();

        // E. 최종 SectionInfo 객체를 생성합니다.
        SectionInfo sectionInfo = SectionInfo.builder()
                .introStartTime(sectionStartTimes.getOrDefault("intro", 0.0))
                .verse1StartTime(sectionStartTimes.getOrDefault("verse1", 0.0))
                .breakStartTime(sectionStartTimes.getOrDefault("break", 0.0))
                .verse2StartTime(sectionStartTimes.getOrDefault("verse2", 0.0))
                .verse1cam(verse1CamInfo)
                .verse2cam(verse2CamInfo)
                .build();

        // --- ▲ 데이터 가공 로직 종료 ▲ ---

        // 1-4. 게임 세션 ID 생성 및 Redis 저장 (이전과 동일)
        String sessionId = UUID.randomUUID().toString();
        GameState initialGameState = GameState.initial(sessionId, user.getId(), song.getId());
        saveGameState(sessionId, initialGameState);
        log.info("새로운 게임 세션 시작: userId={}, sessionId={}", user.getId(), sessionId);

        // 1-5. Presigned URL 생성 (1절 영상 URL 등도 여기서 생성해야 함)
//        String audioUrl = mediaUrlService.issueUrlById(song.getMedia().getId());

        // WebClient를 사용하여 MediaController의 테스트 엔드포인트를 호출합니다.
        // 이 호출들은 동기적으로(순서대로) 작동하여 결과를 받아옵니다.
        String audioUrl = getTestUrl("/media/test");

        Map<String, String> videoUrls = new HashMap<>();
        videoUrls.put("intro", getTestUrl("/media/test/video/break"));
        videoUrls.put("verse1", getTestUrl("/media/test/video/part1"));
        videoUrls.put("verse2_level1", getTestUrl("/media/test/video/part2_1"));
        videoUrls.put("verse2_level2", getTestUrl("/media/test/video/part2_2"));
        // TODO: 2절 3단계 영상 URL을 위한 테스트 엔드포인트가 필요합니다.
        videoUrls.put("verse2_level3", "https://example.com/video_v2_level3.mp4"); // 임시 URL
        // --- ▲ ---------------------------------------------------- ▲ ---

        // 1-6. 최종 응답 생성
        return GameStartResponse.builder()
                .sessionId(sessionId)
                .songId(song.getId())
                .songTitle(song.getTitle())
                .songArtist(song.getArtist())
                .audioUrl(audioUrl)
                .videoUrls(videoUrls)
                .bpm(songBeat.getTempoMap().get(0).getBpm()) // 첫 번째 BPM 값 사용
                .duration(songBeat.getAudio().getDurationSec())
                .sectionInfo(sectionInfo) // 가공된 데이터 전달
                .lyricsInfo(lyricsInfo)     // 가사 정보는 그대로 전달
                .build();
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

        User user = userRepository.findById(finalGameState.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "게임 결과 저장 중 사용자를 찾을 수 없습니다."));
        Song song = songRepository.findById(finalGameState.getSongId())
                .orElseThrow(() -> new CustomException(ErrorCode.SONG_NOT_FOUND, "게임 결과 저장 중 노래를 찾을 수 없습니다."));

        GameResult gameResult = GameResult.builder()
                .user(user)
                .song(song)
                .verse1AvgScore(verse1Avg)
                .verse2AvgScore(verse2Avg)
                .finalLevel(finalGameState.getNextLevel())
                .build();

        gameResultRepository.save(gameResult);
        log.info("세션 {}의 게임 결과 저장 완료. 1절 점수: {}, 2절 점수: {}", sessionId, verse1Avg, verse2Avg);

        gameStateRedisTemplate.delete(sessionId);
        log.info("세션 {}의 Redis 데이터 삭제 완료.", sessionId);
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