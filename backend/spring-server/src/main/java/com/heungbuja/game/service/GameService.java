package com.heungbuja.game.service;

import com.heungbuja.game.dto.FrameBatchRequest;
import com.heungbuja.game.dto.FrameBatchResponse;
import com.heungbuja.game.dto.GameStartRequest;
import com.heungbuja.game.dto.GameStartResponse;
import com.heungbuja.game.state.GameState;
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
import com.heungbuja.game.repository.ActionRepository;
import com.heungbuja.game.entity.GameResult;
import com.heungbuja.game.repository.GameResultRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    // --- 의존성 주입 ---
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final SongBeatRepository songBeatRepository;
    private final SongLyricsRepository songLyricsRepository;
    private final SongChoreographyRepository songChoreographyRepository;
    private final RedisTemplate<String, GameState> gameStateRedisTemplate;
    private final WebClient webClient;
    private final ActionRepository actionRepository;
    private final GameResultRepository gameResultRepository;
    private final com.heungbuja.s3.service.MediaUrlService mediaUrlService;
    // (추가) private final GameResultRepository gameResultRepository; // MySQL 결과 저장을 위한 Repository

    /**
     * 1. 게임 시작 로직
     */
    @Transactional(readOnly = true)
    public GameStartResponse startGame(GameStartRequest request) {
        // 1-1. User 정보 검증
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 ID입니다."));

        if (!user.getIsActive()) {
            throw new IllegalStateException("비활성화된 사용자입니다.");
        }

        // 1-2. MySQL에서 노래 기본 정보 조회
        Song song = songRepository.findById(request.getSongId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 노래 ID입니다."));
        String songTitle = song.getTitle();

        // 1-3. MongoDB에서 노래 상세 메타데이터 조회 (병렬 처리로 성능 향상 가능)
        Mono<SongBeat> beatInfoMono = Mono.fromCallable(() -> songBeatRepository.findByAudioTitle(songTitle).orElseThrow(() -> new IllegalArgumentException("비트 정보를 찾을 수 없습니다.")));
        Mono<SongLyrics> lyricsInfoMono = Mono.fromCallable(() -> songLyricsRepository.findByTitle(songTitle).orElseThrow(() -> new IllegalArgumentException("가사 정보를 찾을 수 없습니다.")));
        Mono<SongChoreography> choreographyInfoMono = Mono.fromCallable(() -> songChoreographyRepository.findBySong(songTitle).orElseThrow(() -> new IllegalArgumentException("안무 정보를 찾을 수 없습니다.")));

        // 3개의 조회 작업이 모두 끝날 때까지 기다림
        var resultTuple = Mono.zip(beatInfoMono, lyricsInfoMono, choreographyInfoMono).block();
        SongBeat beatInfo = resultTuple.getT1();
        SongLyrics lyricsInfo = resultTuple.getT2();
        SongChoreography choreographyInfo = resultTuple.getT3();

        // 1-4. 게임 세션 ID 생성 및 Redis에 초기 상태 저장
        String sessionId = UUID.randomUUID().toString();
        GameState initialGameState = GameState.initial(sessionId, user.getId(), song.getId());
        gameStateRedisTemplate.opsForValue().set(sessionId, initialGameState, Duration.ofMinutes(30)); // 30분 후 세션 만료
        log.info("새로운 게임 세션 시작: userId={}, sessionId={}", user.getId(), sessionId);

        // 1-5. presigned URL 생성
        String presignedUrl = mediaUrlService.issueUrlById(song.getMedia().getId());

        // 1-6. 프론트엔드에 필요한 모든 정보를 담아 응답
        return GameStartResponse.builder()
                .sessionId(sessionId)
                .songId(song.getId())
                .songTitle(song.getTitle())
                .songArtist(song.getArtist())
                .audioUrl(presignedUrl)
                .beatInfo(beatInfo)
                .lyricsInfo(lyricsInfo)
                .choreographyInfo(choreographyInfo)
                .build();
    }

    /**
     * 2. 프레임 묶음 분석 로직
     */
    public FrameBatchResponse analyzeFrameBatch(FrameBatchRequest request) {
        String sessionId = request.getSessionId();
        GameState currentGameState = getGameState(sessionId);

        // 요청 데이터 검증 로직
        if (!currentGameState.getSongId().equals(request.getSongId())) {
            log.warn("세션ID와 노래ID가 일치하지 않습니다! sessionId={}, gameState.songId={}, request.songId={}",
                    sessionId, currentGameState.getSongId(), request.getSongId());
            throw new IllegalArgumentException("세션 정보와 노래 정보가 일치하지 않습니다.");
        }

        // 2-1. Python AI 서버로 분석 요청 (비동기)
        callAiServerAndProcessResult(currentGameState, request);

        // 2-2. 1절의 마지막 묶음 요청인 경우, 레벨 결정 로직 수행
        if (request.getVerse() == 1 && request.isLastSegmentOfVerse1()) {
            // (개선) 모든 AI 응답이 도착했는지 확인하는 더 정교한 로직이 필요할 수 있지만,
            // 우선은 현재까지 Redis에 쌓인 점수 기준으로 즉시 레벨을 결정하여 응답합니다.
            double averageScore = calculateAverageScore(currentGameState.getVerse1Scores());

            int nextLevel = 1;
            if (averageScore >= 80) {
                nextLevel = 3;
            } else if (averageScore >= 60) {
                nextLevel = 2;
            }

            currentGameState.setNextLevel(nextLevel);
            saveGameState(sessionId, currentGameState);
            log.info("세션 {}의 1절 종료. 평균 점수: {}, 다음 레벨: {}", sessionId, averageScore, nextLevel);

            return new FrameBatchResponse("LEVEL_DECIDED", nextLevel);
        }


        // 2-3. 일반적인 경우, 처리 중이라는 응답만 보냄
        return new FrameBatchResponse("PROCESSING", null);
    }

    /**
     * 3. Python AI 서버 호출 및 결과 처리 (채점 로직 포함)
     */
    private void callAiServerAndProcessResult(GameState gameState, FrameBatchRequest request) {
        String sessionId = gameState.getSessionId();

        // AI 서버가 {"action_codes": [0, 1, 0, 1, ...]} 형태로 동작 번호를 반환한다고 가정
        // webClient.post()
        //         .uri("http://motion-server:8000/analyze") // docker-compose 서비스 이름 사용
        //         .bodyValue(request) // request 객체 전체를 보낼 수도 있음
        //         .retrieve()
        //         .bodyToMono(AiResponse.class)
        //         .subscribe(aiResponse -> {
        //             log.info("세션 {}의 분석 결과 도착: {}", sessionId, aiResponse.getActionCodes());
        //
        //             // 4. 채점 진행
        //             double score = grade(gameState.getSongId(), request.getVerse(), request.getSegmentIndex(),
        //                                  request.getDifficulty(), aiResponse.getActionCodes());
        //
        //             // 5. 채점 결과 Redis에 저장
        //             GameState currentState = getGameState(sessionId);
        //             if (request.getVerse() == 1) {
        //                 currentState.getVerse1Scores().add(score);
        //             } else {
        //                 currentState.getVerse2Scores().add(score);
        //             }
        //             saveGameState(sessionId, currentState);
        //             log.info("세션 {}의 {}절-{}번째 묶음 채점 완료. 점수: {}",
        //                      sessionId, request.getVerse(), request.getSegmentIndex(), score);
        //
        //         }, error -> log.error("AI 서버 호출 중 에러 발생 (세션 {}): {}", sessionId, error.getMessage()));

        log.info("세션 {}의 {}절-{}번째 이미지 묶음을 AI 서버로 전송했습니다.",
                sessionId, request.getVerse(), request.getSegmentIndex());
    }

    /**
     * 4. 채점 로직
     * @param songId 현재 노래 ID
     * @param verse 현재 절 (1 또는 2)
     * @param segmentIndex 현재 묶음 인덱스
     * @param difficulty 2절일 경우의 난이도
     * @param userActionCodes AI가 분석한 사용자 동작 번호 리스트
     * @return 0~100점 사이의 점수
     */
    private double grade(Long songId, int verse, int segmentIndex, Integer difficulty, List<Integer> userActionCodes) {
        // TODO: songId, verse 등을 기반으로 MongoDB에서 '정답' 동작 코드 리스트를 가져오는 로직 구현
        // List<Integer> correctActionCodes = findCorrectActionsFromDB(songId, verse, difficulty, segmentIndex);

        List<Integer> correctActionCodes = List.of(0, 0, 1, 1, 1, 1); // 임시 정답지

        // (참고) 만약 점수 외에 "틀린 동작 이름" 같은 정보를 알려주고 싶다면,
        // actionRepository를 사용하여 코드로부터 동작 이름을 조회할 수 있습니다.
        // ex: Action wrongAction = actionRepository.findByActionCode(userActionCodes.get(i)).orElse(null);

        int correctCount = 0;
        int totalActions = correctActionCodes.size();
        for (int i = 0; i < totalActions; i++) {
            if (i < userActionCodes.size() && correctActionCodes.get(i).equals(userActionCodes.get(i))) {
                correctCount++;
            }
        }

        return (double) correctCount / totalActions * 100.0;
    }

    /**
     * (추가) 게임 종료 및 결과 저장
     */
    @Transactional // DB에 데이터를 쓰는 작업이므로 @Transactional 어노테이션 추가
    public void endGame(String sessionId) {
        // Redis에서 최종 게임 상태를 가져옵니다.
        GameState finalGameState = getGameState(sessionId);
        if (finalGameState == null) {
            log.warn("이미 처리되었거나 존재하지 않는 세션 ID로 종료 요청: {}", sessionId);
            return; // 이미 삭제되었으면 아무것도 하지 않음
        }

        // 1. 결과 계산
        double verse1Avg = calculateAverageScore(finalGameState.getVerse1Scores());
        double verse2Avg = calculateAverageScore(finalGameState.getVerse2Scores());

        // 2. GameResult 엔티티 생성을 위해 User와 Song 엔티티를 DB에서 조회
        User user = userRepository.findById(finalGameState.getUserId())
                .orElseThrow(() -> new IllegalStateException("게임 결과 저장 중 사용자를 찾을 수 없습니다."));
        Song song = songRepository.findById(finalGameState.getSongId())
                .orElseThrow(() -> new IllegalStateException("게임 결과 저장 중 노래를 찾을 수 없습니다."));

        // 3. GameResult 엔티티 객체 생성
        GameResult gameResult = GameResult.builder()
                .user(user)
                .song(song)
                .verse1AvgScore(verse1Avg)
                .verse2AvgScore(verse2Avg)
                .finalLevel(finalGameState.getNextLevel())
                .build();

        // 4. MySQL에 최종 결과 저장
        gameResultRepository.save(gameResult);
        log.info("세션 {}의 게임 결과 저장 완료. User: {}, Song: {}", sessionId, user.getId(), song.getId());

        // 5. Redis에 저장된 임시 데이터 삭제
        gameStateRedisTemplate.delete(sessionId);
        log.info("세션 {}의 Redis 데이터 삭제 완료.", sessionId);
    }


    // --- Helper 메소드들 ---
    private GameState getGameState(String sessionId) {
        GameState gameState = gameStateRedisTemplate.opsForValue().get(sessionId);
        if (gameState == null) {
            throw new IllegalArgumentException("만료되었거나 유효하지 않은 세션 ID입니다: " + sessionId);
        }
        return gameState;
    }

    private void saveGameState(String sessionId, GameState gameState) {
        gameStateRedisTemplate.opsForValue().set(sessionId, gameState, Duration.ofMinutes(30));
    }

    private double calculateAverageScore(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    // (추가) AI 서버의 응답을 받을 DTO
    // @Getter
    // private static class AiResponse {
    //     private List<String> actions;
    // }
}