package com.heungbuja.command.service.impl;

import com.heungbuja.command.dto.CommandRequest;
import com.heungbuja.command.dto.CommandResponse;
import com.heungbuja.command.dto.IntentResult;
import com.heungbuja.command.service.CommandService;
import com.heungbuja.command.service.IntentClassifier;
import com.heungbuja.command.service.ResponseGenerator;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.emergency.dto.EmergencyRequest;
import com.heungbuja.emergency.service.EmergencyService;
import com.heungbuja.song.dto.SongInfoDto;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.enums.PlaybackMode;
import com.heungbuja.song.service.ListeningHistoryService;
import com.heungbuja.song.service.SongService;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.service.UserService;
import com.heungbuja.voice.entity.VoiceCommand;
import com.heungbuja.voice.enums.Intent;
import com.heungbuja.voice.repository.VoiceCommandRepository;
import com.heungbuja.voice.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 통합 명령 처리 서비스 구현체
 * 의도 분석 → 적절한 서비스 호출 → 응답 생성
 *
 * 주의: 프론트엔드가 음악 재생을 관리하므로, 백엔드는:
 * - 노래 정보만 전달 (audioUrl)
 * - 청취 이력만 기록
 * - 상태 관리 없음
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommandServiceImpl implements CommandService {

    // 핵심 서비스 (인터페이스 기반 - 느슨한 결합)
    private final IntentClassifier intentClassifier;
    private final TtsService ttsService;

    // 도메인 서비스
    private final UserService userService;
    private final SongService songService;
    private final ListeningHistoryService listeningHistoryService;
    private final EmergencyService emergencyService;
    private final com.heungbuja.s3.service.MediaUrlService mediaUrlService;
    private final com.heungbuja.context.service.ConversationContextService conversationContextService;
    private final com.heungbuja.session.service.SessionStateService sessionStateService;

    // 세션 서비스
    private final com.heungbuja.session.service.SessionPrepareService sessionPrepareService;

    // Song 캐시
    private final com.heungbuja.song.service.SongGameDataCache songGameDataCache;

    // 기타
    private final VoiceCommandRepository voiceCommandRepository;
    private final ResponseGenerator responseGenerator;

    // 활동 로그
    private final com.heungbuja.activity.service.ActivityLogService activityLogService;

    /**
     * 텍스트 명령어 처리 (통합 엔드포인트)
     *
     * noRollbackFor: CustomException을 Controller에서 catch하므로
     * 트랜잭션을 롤백하지 않도록 설정
     */
    @Override
    @Transactional(noRollbackFor = CustomException.class)
    public CommandResponse processTextCommand(CommandRequest request) {
        User user = userService.findById(request.getUserId());
        String text = request.getText().trim();

        log.info("명령 처리 시작: userId={}, text='{}'", user.getId(), text);

        try {
            // 1. 의도 분석 (IntentClassifier - 교체 가능)
            IntentResult intentResult = intentClassifier.classify(text, user.getId());
            Intent intent = intentResult.getIntent();

            // 원본 텍스트 저장 (Emergency 등에서 사용)
            intentResult = IntentResult.builder()
                    .intent(intentResult.getIntent())
                    .entities(intentResult.getEntities())
                    .confidence(intentResult.getConfidence())
                    .rawText(text)
                    .build();

            log.info("의도 분석 완료: intent={}, classifier={}", intent, intentClassifier.getClassifierType());

            // 2. 음성 명령 로그 저장
            saveVoiceCommand(user, text, intent);

            // 3. 활동 로그 저장 (관리자 페이지용 익명화)
            saveActivityLog(user, intent, intentResult);

            // 4. 의도에 따른 처리
            CommandResponse response = executeIntent(user, intentResult);

            log.info("명령 처리 완료: userId={}, intent={}, success={}", user.getId(), intent, response.isSuccess());

            return response;

        } catch (CustomException e) {
            // CustomException은 그대로 던져서 Controller에서 적절한 HTTP 상태로 변환
            throw e;
        } catch (Exception e) {
            // 예상치 못한 에러는 COMMAND_EXECUTION_FAILED로 변환
            log.error("명령 처리 실패: userId={}, text='{}'", user.getId(), text, e);
            throw new CustomException(ErrorCode.COMMAND_EXECUTION_FAILED, "명령 처리 중 오류가 발생했습니다");
        }
    }

    /**
     * 의도에 따른 실행
     */
    private CommandResponse executeIntent(User user, IntentResult intentResult) {
        Intent intent = intentResult.getIntent();

        return switch (intent) {
            // 노래 검색
            case SELECT_BY_ARTIST -> handleSearchByArtist(user, intentResult);
            case SELECT_BY_TITLE -> handleSearchByTitle(user, intentResult);
            case SELECT_BY_ARTIST_TITLE -> handleSearchByArtistAndTitle(user, intentResult);

            // 재생 제어 (프론트가 관리하므로 TTS 응답만)
            case MUSIC_PAUSE -> handleSimpleResponse(user, Intent.MUSIC_PAUSE);
            case MUSIC_RESUME -> handleSimpleResponse(user, Intent.MUSIC_RESUME);
            case MUSIC_NEXT -> handleSimpleResponse(user, Intent.MUSIC_NEXT);
            case MUSIC_STOP -> handleSimpleResponse(user, Intent.MUSIC_STOP);

            // 연속 재생
            case PLAY_NEXT_IN_QUEUE -> handlePlayNextInQueue(user);
            case PLAY_MORE_LIKE_THIS -> handlePlayMoreLikeThis(user);

            // 모드 관련 (프론트가 관리하므로 TTS 응답만)
            case MODE_HOME -> handleModeChange(user, Intent.MODE_HOME, PlaybackMode.HOME);
            case MODE_LISTENING -> handleModeChange(user, Intent.MODE_LISTENING, PlaybackMode.LISTENING);
            case MODE_EXERCISE_NO_SONG -> handleGameListScreen(user);
            case MODE_EXERCISE_END -> handleGameEnd(user);

            // 응급 상황
            case EMERGENCY -> handleEmergency(user, intentResult);
            case EMERGENCY_CANCEL -> handleEmergencyCancel(user);
            case EMERGENCY_CONFIRM -> handleEmergencyConfirm(user, intentResult);

            // 인식 불가
            case UNKNOWN -> handleUnknown();

            default -> handleError(intent);
        };
    }

    /**
     * 가수명으로 노래 검색
     */
    private CommandResponse handleSearchByArtist(User user, IntentResult intentResult) {
        String query = intentResult.getEntity("query");
        if (query == null) query = intentResult.getEntity("artist");

        Song song = songService.searchByArtist(query);

        // 게임 진행 중이면 중단
        handleActivityInterrupt(user.getId(), "음악 재생");

        // 청취 이력 기록
        listeningHistoryService.recordListening(user, song, PlaybackMode.LISTENING);

        // Redis: 컨텍스트 업데이트 (모드 + 현재 곡)
        conversationContextService.changeMode(user.getId(), PlaybackMode.LISTENING);
        conversationContextService.setCurrentSong(user.getId(), song.getId());

        // Redis: 활동 상태 업데이트
        sessionStateService.setCurrentActivity(user.getId(),
            com.heungbuja.session.state.ActivityState.music(String.valueOf(song.getId())));

        // presigned URL 생성
        String presignedUrl = mediaUrlService.issueUrlById(song.getMedia().getId());

        String responseText = responseGenerator.generateResponse(Intent.SELECT_BY_ARTIST, song.getArtist(), song.getTitle());

        // 화면 전환 정보 생성
        Map<String, Object> screenData = new HashMap<>();
        screenData.put("songId", song.getId());
        screenData.put("autoPlay", true);

        CommandResponse.ScreenTransition screenTransition = CommandResponse.ScreenTransition.builder()
                .targetScreen("/listening")
                .action("PLAY_SONG")
                .data(screenData)
                .build();

        return CommandResponse.withSongAndScreen(
                Intent.SELECT_BY_ARTIST,
                responseText,
                SongInfoDto.from(song, PlaybackMode.LISTENING, presignedUrl),
                screenTransition
        );
    }

    /**
     * 제목으로 노래 검색
     */
    private CommandResponse handleSearchByTitle(User user, IntentResult intentResult) {
        String title = intentResult.getEntity("title");
        Song song = songService.searchByTitle(title);

        // 게임 진행 중이면 중단
        handleActivityInterrupt(user.getId(), "음악 재생");

        // 청취 이력 기록
        listeningHistoryService.recordListening(user, song, PlaybackMode.LISTENING);

        // Redis: 컨텍스트 업데이트 (모드 + 현재 곡)
        conversationContextService.changeMode(user.getId(), PlaybackMode.LISTENING);
        conversationContextService.setCurrentSong(user.getId(), song.getId());

        // Redis: 활동 상태 업데이트
        sessionStateService.setCurrentActivity(user.getId(),
            com.heungbuja.session.state.ActivityState.music(String.valueOf(song.getId())));

        // presigned URL 생성
        String presignedUrl = mediaUrlService.issueUrlById(song.getMedia().getId());

        String responseText = responseGenerator.generateResponse(Intent.SELECT_BY_TITLE, song.getArtist(), song.getTitle());

        // 화면 전환 정보 생성
        Map<String, Object> screenData = new HashMap<>();
        screenData.put("songId", song.getId());
        screenData.put("autoPlay", true);

        CommandResponse.ScreenTransition screenTransition = CommandResponse.ScreenTransition.builder()
                .targetScreen("/listening")
                .action("PLAY_SONG")
                .data(screenData)
                .build();

        return CommandResponse.withSongAndScreen(
                Intent.SELECT_BY_TITLE,
                responseText,
                SongInfoDto.from(song, PlaybackMode.LISTENING, presignedUrl),
                screenTransition
        );
    }

    /**
     * 가수+제목으로 노래 검색
     */
    private CommandResponse handleSearchByArtistAndTitle(User user, IntentResult intentResult) {
        String artist = intentResult.getEntity("artist");
        String title = intentResult.getEntity("title");

        Song song = songService.searchByArtistAndTitle(artist, title);

        // 게임 진행 중이면 중단
        handleActivityInterrupt(user.getId(), "음악 재생");

        // 청취 이력 기록
        listeningHistoryService.recordListening(user, song, PlaybackMode.LISTENING);

        // Redis: 컨텍스트 업데이트 (모드 + 현재 곡)
        conversationContextService.changeMode(user.getId(), PlaybackMode.LISTENING);
        conversationContextService.setCurrentSong(user.getId(), song.getId());

        // Redis: 활동 상태 업데이트
        sessionStateService.setCurrentActivity(user.getId(),
            com.heungbuja.session.state.ActivityState.music(String.valueOf(song.getId())));

        // presigned URL 생성
        String presignedUrl = mediaUrlService.issueUrlById(song.getMedia().getId());

        String responseText = responseGenerator.generateResponse(Intent.SELECT_BY_ARTIST_TITLE, song.getArtist(), song.getTitle());

        // 화면 전환 정보 생성
        Map<String, Object> screenData = new HashMap<>();
        screenData.put("songId", song.getId());
        screenData.put("autoPlay", true);

        CommandResponse.ScreenTransition screenTransition = CommandResponse.ScreenTransition.builder()
                .targetScreen("/listening")
                .action("PLAY_SONG")
                .data(screenData)
                .build();

        return CommandResponse.withSongAndScreen(
                Intent.SELECT_BY_ARTIST_TITLE,
                responseText,
                SongInfoDto.from(song, PlaybackMode.LISTENING, presignedUrl),
                screenTransition
        );
    }

    /**
     * 단순 응답 (TTS만)
     * 프론트가 재생을 관리하므로 백엔드는 음성 안내만
     */
    private CommandResponse handleSimpleResponse(User user, Intent intent) {
        String responseText = responseGenerator.generateResponse(intent);
        String ttsUrl = ttsService.synthesize(responseText);

        return CommandResponse.success(intent, responseText, "/commands/tts/" + ttsUrl);
    }

    /**
     * 모드 변경 처리
     * Redis 컨텍스트 업데이트 포함
     */
    private CommandResponse handleModeChange(User user, Intent intent, PlaybackMode newMode) {
        // Redis: 모드 변경
        conversationContextService.changeMode(user.getId(), newMode);

        String responseText = responseGenerator.generateResponse(intent);
        String ttsUrl = ttsService.synthesize(responseText);

        return CommandResponse.success(intent, responseText, "/commands/tts/" + ttsUrl);
    }

    /**
     * 게임 목록 화면 처리 (노래 없이 게임 시작)
     */
    private CommandResponse handleGameListScreen(User user) {
        log.info("게임 목록 화면 이동 요청: userId={}", user.getId());

        // 1. 음악/게임 진행 중이면 중단
        handleActivityInterrupt(user.getId(), "게임 목록");

        // 2. ConversationContext 모드 변경
        conversationContextService.changeMode(user.getId(), PlaybackMode.EXERCISE);

        // 3. 응답 생성
        String responseText = "게임 목록을 보여드릴게요";

        CommandResponse.ScreenTransition screenTransition = CommandResponse.ScreenTransition.builder()
                .targetScreen("/game/list")
                .action("SHOW_GAME_LIST")
                .data(Map.of())
                .build();

        return CommandResponse.builder()
                .success(true)
                .intent(Intent.MODE_EXERCISE_NO_SONG)
                .responseText(responseText)
                .ttsAudioUrl(null)
                .songInfo(null)
                .screenTransition(screenTransition)
                .build();
    }

    /**
     * 게임 종료 처리 (Command가 ActivityState 조회)
     */
    private CommandResponse handleGameEnd(User user) {
        // Command가 ActivityState 조회!
        com.heungbuja.session.state.ActivityState currentActivity =
                sessionStateService.getCurrentActivity(user.getId());

        if (currentActivity.getType() == com.heungbuja.session.enums.ActivityType.GAME) {
            String sessionId = currentActivity.getSessionId();
            log.info("게임 종료 요청: userId={}, sessionId={}", user.getId(), sessionId);

            // Redis 상태만 변경 (GameService가 감지)
            if (sessionStateService.trySetInterrupt(sessionId, "USER_END")) {
                log.info("게임 중단 플래그 설정: sessionId={}", sessionId);
            } else {
                log.warn("게임 중단 플래그 설정 실패 (이미 처리 중): sessionId={}", sessionId);
            }
        } else {
            log.warn("게임 진행 중이 아님: userId={}, currentType={}",
                    user.getId(), currentActivity.getType());
        }

        String responseText = responseGenerator.generateResponse(Intent.MODE_EXERCISE_END);

        // ConversationContext 모드 변경
        conversationContextService.changeMode(user.getId(), PlaybackMode.HOME);

        // 화면 전환
        CommandResponse.ScreenTransition screenTransition = CommandResponse.ScreenTransition.builder()
                .targetScreen("/home")
                .action("END_GAME")
                .data(Map.of())
                .build();

        return CommandResponse.builder()
                .success(true)
                .intent(Intent.MODE_EXERCISE_END)
                .responseText(responseText)
                .ttsAudioUrl(null)
                .songInfo(null)
                .screenTransition(screenTransition)
                .build();
    }

    /**
     * 응급 상황 처리
     */
    private CommandResponse handleEmergency(User user, IntentResult intentResult) {
        // triggerWord: entities에서 keyword를 가져오거나, 없으면 원본 텍스트 사용
        String triggerWord = intentResult.getEntity("keyword");
        if (triggerWord == null || triggerWord.isBlank()) {
            triggerWord = intentResult.getRawText();
        }

        // 응급 신고 생성
        EmergencyRequest emergencyRequest = EmergencyRequest.builder()
                .userId(user.getId())
                .triggerWord(triggerWord)
                .fullText(intentResult.getRawText())  // 전체 발화 텍스트
                .build();

        emergencyService.detectEmergencyWithSchedule(emergencyRequest);

        String responseText = responseGenerator.generateResponse(Intent.EMERGENCY);
        String ttsUrl = ttsService.synthesize(responseText, "urgent"); // 긴급 음성 타입

        return CommandResponse.success(Intent.EMERGENCY, responseText, "/commands/tts/" + ttsUrl);
    }

    /**
     * 응급 상황 취소 처리
     */
    private CommandResponse handleEmergencyCancel(User user) {
        emergencyService.cancelRecentReport(user.getId());

        String responseText = responseGenerator.generateResponse(Intent.EMERGENCY_CANCEL);
        String ttsUrl = ttsService.synthesize(responseText);

        return CommandResponse.success(Intent.EMERGENCY_CANCEL, responseText, "/commands/tts/" + ttsUrl);
    }

    /**
     * 응급 상황 즉시 확정 처리
     */
    private CommandResponse handleEmergencyConfirm(User user, IntentResult intentResult) {
        emergencyService.confirmRecentReport(user.getId());

        String responseText = responseGenerator.generateResponse(Intent.EMERGENCY_CONFIRM);
        String ttsUrl = ttsService.synthesize(responseText, "urgent"); // 긴급 음성 타입

        return CommandResponse.success(Intent.EMERGENCY_CONFIRM, responseText, "/commands/tts/" + ttsUrl);
    }

    /**
     * 인식 불가
     */
    private CommandResponse handleUnknown() {
//        String responseText = responseGenerator.generateResponse(Intent.UNKNOWN);
        String responseText = "잘 듣지 못했어요. 다시 말씀해주세요";
        String ttsUrl = ttsService.synthesize(responseText);

        return CommandResponse.failure(Intent.UNKNOWN, responseText, "/commands/tts/" + ttsUrl);
    }

    /**
     * 대기열 다음 곡 재생
     * TODO: Redis에서 대기열 가져오기
     */
    private CommandResponse handlePlayNextInQueue(User user) {
        // Redis가 아직 없으므로 현재는 간단한 응답만
        // TODO: Redis에서 사용자 대기열(queue)을 가져와서 다음 곡 재생
        String responseText = "대기열이 비어있어요";
        String ttsUrl = ttsService.synthesize(responseText);

        return CommandResponse.failure(Intent.PLAY_NEXT_IN_QUEUE, responseText, "/commands/tts/" + ttsUrl);
    }

    /**
     * 비슷한 노래 계속 재생
     * 최근 청취한 노래의 가수와 같은 가수의 다른 노래를 재생
     */
    private CommandResponse handlePlayMoreLikeThis(User user) {
        // 최근 청취 곡 가져오기
        var recentHistory = listeningHistoryService.getRecentHistory(user, 1);

        if (recentHistory.isEmpty()) {
            String responseText = "최근 들으신 노래가 없어요";
            String ttsUrl = ttsService.synthesize(responseText);
            return CommandResponse.failure(Intent.PLAY_MORE_LIKE_THIS, responseText, "/commands/tts/" + ttsUrl);
        }

        Song lastSong = recentHistory.get(0).getSong();
        String artist = lastSong.getArtist();

        try {
            // 같은 가수의 노래 검색
            // TODO: 이미 들은 곡을 제외하는 로직 추가 (Redis 대기열 활용)
            Song similarSong = songService.searchByArtist(artist);

            // 청취 이력 기록
            listeningHistoryService.recordListening(user, similarSong, PlaybackMode.LISTENING);

            // Redis: 컨텍스트 업데이트 (현재 곡)
            conversationContextService.setCurrentSong(user.getId(), similarSong.getId());

            // presigned URL 생성
            String presignedUrl = mediaUrlService.issueUrlById(similarSong.getMedia().getId());

            String responseText = responseGenerator.generateResponse(Intent.PLAY_MORE_LIKE_THIS);

            // 화면 전환 정보 생성
            Map<String, Object> screenData = new HashMap<>();
            screenData.put("songId", similarSong.getId());
            screenData.put("autoPlay", true);

            CommandResponse.ScreenTransition screenTransition = CommandResponse.ScreenTransition.builder()
                    .targetScreen("/listening")
                    .action("PLAY_SONG")
                    .data(screenData)
                    .build();

            return CommandResponse.withSongAndScreen(
                    Intent.PLAY_MORE_LIKE_THIS,
                    responseText,
                    SongInfoDto.from(similarSong, PlaybackMode.LISTENING, presignedUrl),
                    screenTransition
            );
        } catch (CustomException e) {
            // 같은 가수의 곡을 찾지 못한 경우
            String responseText = "추천할 노래가 없어요";
            String ttsUrl = ttsService.synthesize(responseText);
            return CommandResponse.failure(Intent.PLAY_MORE_LIKE_THIS, responseText, "/commands/tts/" + ttsUrl);
        }
    }

    /**
     * 에러 처리
     */
    private CommandResponse handleError(Intent intent) {
        log.warn("처리되지 않은 Intent: {}", intent);

        String errorMsg = responseGenerator.errorMessage();
        String ttsUrl = ttsService.synthesize(errorMsg);

        // 원래 intent를 유지하면서 실패 응답 반환
        return CommandResponse.failure(intent, errorMsg, "/commands/tts/" + ttsUrl);
    }

    /**
     * 음성 명령 로그 저장
     */
    private void saveVoiceCommand(User user, String text, Intent intent) {
        VoiceCommand command = VoiceCommand.builder()
                .user(user)
                .rawText(text)
                .intent(intent.name())
                .build();

        voiceCommandRepository.save(command);
    }

    /**
     * 활동 로그 저장 (관리자 페이지용 익명화)
     */
    private void saveActivityLog(User user, Intent intent, IntentResult intentResult) {
        activityLogService.saveActivityLog(user, intent);
    }

    /**
     * 현재 활동 인터럽트 처리 (Command가 ActivityState 조회)
     */
    private void handleActivityInterrupt(Long userId, String newActivity) {
        // Command가 ActivityState 조회!
        com.heungbuja.session.state.ActivityState currentActivity =
                sessionStateService.getCurrentActivity(userId);

        if (currentActivity.getType() == com.heungbuja.session.enums.ActivityType.IDLE) {
            return;
        }

        log.info("활동 인터럽트: userId={}, 현재={}, 새활동={}",
                userId, currentActivity.getType(), newActivity);

        switch (currentActivity.getType()) {
            case GAME:
                // sessionId 획득 후 Redis 플래그만 설정
                String sessionId = currentActivity.getSessionId();
                if (sessionStateService.trySetInterrupt(sessionId, newActivity)) {
                    log.info("게임 중단 플래그 설정: sessionId={}, reason={}", sessionId, newActivity);
                }
                break;

            case MUSIC:
                // 음악은 바로 정리 (프론트가 관리)
                sessionStateService.clearActivity(userId);
                log.info("음악 중단: userId={}", userId);
                break;

            case EMERGENCY:
                log.warn("응급 상황 중에는 다른 활동 불가: userId={}", userId);
                break;

            default:
                break;
        }
    }
}
