package com.heungbuja.command.mcp;

import com.heungbuja.command.mcp.dto.McpToolCall;
import com.heungbuja.command.mcp.dto.McpToolResult;
import com.heungbuja.command.service.ResponseGenerator;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.context.entity.ConversationContext;
import com.heungbuja.context.service.ConversationContextService;
import com.heungbuja.emergency.dto.EmergencyRequest;
import com.heungbuja.emergency.service.EmergencyService;
import com.heungbuja.s3.service.MediaUrlService;
import com.heungbuja.song.dto.SongInfoDto;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.enums.PlaybackMode;
import com.heungbuja.song.service.ListeningHistoryService;
import com.heungbuja.song.service.SongService;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool 실행 서비스
 * GPT가 호출하는 Tool들의 실제 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpToolService {

    private final UserService userService;
    private final SongService songService;
    private final ListeningHistoryService listeningHistoryService;
    private final ConversationContextService conversationContextService;
    private final MediaUrlService mediaUrlService;
    private final EmergencyService emergencyService;
    private final ResponseGenerator responseGenerator;
    private final com.heungbuja.session.service.SessionPrepareService sessionPrepareService;
    private final com.heungbuja.song.service.SongGameDataCache songGameDataCache;
//    private final com.heungbuja.game.service.GameService gameService;

    /**
     * Tool 호출 실행
     */
    @Transactional
    public McpToolResult executeTool(McpToolCall toolCall) {
        log.info("MCP Tool 실행: name={}, args={}", toolCall.getName(), toolCall.getArguments());

        try {
            return switch (toolCall.getName()) {
                case "search_song" -> searchSong(toolCall);
                case "control_playback" -> controlPlayback(toolCall);
                case "add_to_queue" -> addToQueue(toolCall);
                case "get_current_context" -> getCurrentContext(toolCall);
                case "handle_emergency" -> handleEmergency(toolCall);
                case "cancel_emergency" -> cancelEmergency(toolCall);
                case "confirm_emergency" -> confirmEmergency(toolCall);
                case "change_mode" -> changeMode(toolCall);
                case "start_game" -> startGame(toolCall);
                case "start_game_with_song" -> startGameWithSong(toolCall);
                default -> McpToolResult.failure(
                        toolCall.getId(),
                        toolCall.getName(),
                        "알 수 없는 Tool입니다: " + toolCall.getName()
                );
            };

        } catch (CustomException e) {
            log.error("Tool 실행 실패 (CustomException): tool={}, error={}",
                    toolCall.getName(), e.getMessage(), e);
            return McpToolResult.failure(toolCall.getId(), toolCall.getName(), e.getMessage());

        } catch (Exception e) {
            log.error("Tool 실행 실패 (Exception): tool={}", toolCall.getName(), e);
            return McpToolResult.failure(
                    toolCall.getId(),
                    toolCall.getName(),
                    "Tool 실행 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    /**
     * Tool: search_song
     * 가수명, 제목, 연대, 장르, 분위기로 노래 검색
     */
    private McpToolResult searchSong(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");
        String artist = getStringArg(args, "artist");
        String title = getStringArg(args, "title");
        String era = getStringArg(args, "era");
        String genre = getStringArg(args, "genre");
        String mood = getStringArg(args, "mood");
        Long excludeSongId = getLongArg(args, "excludeSongId");

        // 빈 문자열을 null로 처리
        artist = (artist != null && artist.trim().isEmpty()) ? null : artist;
        title = (title != null && title.trim().isEmpty()) ? null : title;

        User user = userService.findById(userId);

        // 노래 검색 (현재는 기본 검색만, 향후 era, genre, mood 활용 가능)
        Song song;
        try {
            if (artist != null && title != null) {
                song = songService.searchByArtistAndTitle(artist, title);
            } else if (artist != null) {
                song = songService.searchByArtist(artist);
            } else if (title != null) {
                song = songService.searchByTitle(title);
            } else {
                // TODO: era, genre, mood 기반 검색 구현
                throw new IllegalArgumentException("artist 또는 title이 필요합니다");
            }
        } catch (CustomException e) {
            // 노래를 찾지 못한 경우 간단한 로그만 출력
            log.warn("노래 없음: artist={}, title={}", artist, title);
            return McpToolResult.failure(
                    toolCall.getId(),
                    toolCall.getName(),
                    "노래를 찾을 수 없습니다"
            );
        }

        // excludeSongId 체크
        if (excludeSongId != null && song.getId().equals(excludeSongId)) {
            // TODO: 같은 조건으로 다른 노래 검색
            log.warn("검색된 노래가 제외 대상과 동일: songId={}", excludeSongId);
        }

        // 청취 이력 기록
        listeningHistoryService.recordListening(user, song, PlaybackMode.LISTENING);

        // Redis Context 업데이트
        conversationContextService.changeMode(userId, PlaybackMode.LISTENING);
        conversationContextService.setCurrentSong(userId, song.getId());

        // Presigned URL 생성
        String presignedUrl = mediaUrlService.issueUrlById(song.getMedia().getId());

        SongInfoDto songInfo = SongInfoDto.from(song, PlaybackMode.LISTENING, presignedUrl);

        return McpToolResult.success(toolCall.getId(), toolCall.getName(), songInfo);
    }

    /**
     * Tool: control_playback
     * 재생 제어 (PAUSE, RESUME, NEXT, STOP)
     */
    private McpToolResult controlPlayback(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");
        String action = getStringArg(args, "action");

        if (action == null) {
            throw new IllegalArgumentException("action이 필요합니다");
        }

        String message = switch (action.toUpperCase()) {
            case "PAUSE" -> "일시정지할게요";
            case "RESUME" -> "다시 재생할게요";
            case "NEXT" -> "다음 곡으로 넘어갈게요";
            case "STOP" -> "재생을 멈출게요";
            default -> throw new IllegalArgumentException("알 수 없는 action: " + action);
        };

        // action 정보를 data에 포함 (McpCommandServiceImpl에서 Intent 매핑에 사용)
        Map<String, Object> data = new HashMap<>();
        data.put("action", action.toUpperCase());

        return McpToolResult.success(toolCall.getId(), toolCall.getName(), message, data);
    }

    /**
     * Tool: add_to_queue
     * 대기열에 곡 추가
     */
    private McpToolResult addToQueue(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");
        String artist = getStringArg(args, "artist");
        Integer count = getIntegerArg(args, "count", 1);

        if (artist == null) {
            throw new IllegalArgumentException("artist가 필요합니다");
        }

        // artist의 노래를 count개 검색 (현재는 1개만, 향후 확장 가능)
        List<Long> songIds = new ArrayList<>();

        // TODO: artist의 노래를 count개 검색하는 로직
        // 현재는 1곡만 검색
        Song song = songService.searchByArtist(artist);
        songIds.add(song.getId());

        // Redis에 대기열 추가
        conversationContextService.addAllToQueue(userId, songIds);

        String message = String.format("%s의 노래 %d곡을 대기열에 추가했어요", artist, songIds.size());

        Map<String, Object> data = new HashMap<>();
        data.put("addedSongs", songIds.size());
        data.put("artist", artist);

        return McpToolResult.success(toolCall.getId(), toolCall.getName(), message, data);
    }

    /**
     * Tool: get_current_context
     * 현재 재생 상태, 대기열 정보 조회
     */
    private McpToolResult getCurrentContext(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();
        Long userId = getLongArg(args, "userId");

        ConversationContext context = conversationContextService.getOrCreate(userId);

        Map<String, Object> contextData = new HashMap<>();
        contextData.put("mode", context.getCurrentMode().name());
        contextData.put("currentSongId", context.getCurrentSongId());
        contextData.put("queueSize", context.getPlaylistQueue() != null ? context.getPlaylistQueue().size() : 0);
        contextData.put("lastInteraction", context.getLastInteractionAt().toString());

        // 현재 곡 정보 조회
        if (context.getCurrentSongId() != null) {
            try {
                Song song = songService.findById(context.getCurrentSongId());
                contextData.put("currentSongArtist", song.getArtist());
                contextData.put("currentSongTitle", song.getTitle());
            } catch (Exception e) {
                // 노래를 찾을 수 없으면 무시
                log.warn("현재 곡을 찾을 수 없습니다: {}", context.getCurrentSongId());
            }
        }

        String message = conversationContextService.formatContextForGpt(userId);

        return McpToolResult.success(toolCall.getId(), toolCall.getName(), message, contextData);
    }

    /**
     * Tool: handle_emergency
     * 응급 상황 처리
     */
    private McpToolResult handleEmergency(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");
        String keyword = getStringArg(args, "keyword");
        String fullText = getStringArg(args, "fullText");

        User user = userService.findById(userId);

        EmergencyRequest request = EmergencyRequest.builder()
                .userId(userId)
                .triggerWord(keyword != null ? keyword : fullText)
                .fullText(fullText)
                .build();

        emergencyService.detectEmergencyWithSchedule(request);

        String message = "괜찮으세요? 대답해주세요!";

        return McpToolResult.success(toolCall.getId(), toolCall.getName(), message);
    }

    /**
     * Tool: cancel_emergency
     * 응급 신고 취소 (사용자가 괜찮다고 응답)
     */
    private McpToolResult cancelEmergency(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");

        emergencyService.cancelRecentReport(userId);

        String message = "괜찮으시군요. 신고를 취소했습니다";

        return McpToolResult.success(toolCall.getId(), toolCall.getName(), message);
    }

    /**
     * Tool: confirm_emergency
     * 응급 신고 즉시 확정 (사용자가 "안 괜찮아", "빨리 신고해" 등으로 응답)
     */
    private McpToolResult confirmEmergency(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");

        emergencyService.confirmRecentReport(userId);

        String message = "알겠습니다. 지금 바로 신고하겠습니다";

        return McpToolResult.success(toolCall.getId(), toolCall.getName(), message);
    }

    /**
     * Tool: change_mode
     * 모드 변경 (HOME, LISTENING, EXERCISE)
     */
    private McpToolResult changeMode(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");
        String modeStr = getStringArg(args, "mode");

        if (modeStr == null) {
            throw new IllegalArgumentException("mode가 필요합니다");
        }

        PlaybackMode mode = PlaybackMode.valueOf(modeStr.toUpperCase());
        conversationContextService.changeMode(userId, mode);

        String message = switch (mode) {
            case HOME -> "홈 화면으로 이동할게요";
            case LISTENING -> "노래 감상 모드로 전환할게요";
            case EXERCISE -> "체조 모드를 시작할게요";
        };

        // mode 정보를 data에 포함 (McpCommandServiceImpl에서 Intent 매핑에 사용)
        Map<String, Object> data = new HashMap<>();
        data.put("mode", mode.name());

        return McpToolResult.success(toolCall.getId(), toolCall.getName(), message, data);
    }

    /**
     * Tool: start_game
     * 게임(체조)을 시작합니다
     *
     * 주의: 이 메서드는 게임 시작만 처리하고 즉시 응답합니다 (1-2초 소요)
     *       게임 진행(3-5분)은 프론트엔드가 /game/frame으로 별도 처리합니다
     */
    private McpToolResult startGame(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");
        Long songId = getLongArg(args, "songId");

        // ----------------------- 수정 --------------------------------------------

        // 1. 노래 ID 결정 (songId가 없으면 context에서 가져오기)
        if (songId == null) {
            log.info("songId가 없음, context에서 현재 곡 조회 시도");
            ConversationContext context = conversationContextService.getOrCreate(userId);
            songId = context.getCurrentSongId();

            if (songId == null) {
                throw new IllegalArgumentException("게임 시작을 위한 노래가 없습니다. 먼저 노래를 선택해주세요.");
            }
            log.info("context에서 songId 획득: {}", songId);
        }

        // 노래 존재 여부 확인
        Song song = songService.findById(songId);

        // 2. Song 게임 데이터 조회 (MongoDB)
        com.heungbuja.song.dto.SongGameData songGameData =
                songGameDataCache.getOrLoadSongGameData(songId);

        // 3. audioUrl 생성
        String audioUrl = mediaUrlService.issueUrlById(song.getMedia().getId());

        // 4. SessionPrepareService로 GameState 생성 (Redis 저장)
        com.heungbuja.game.dto.GameSessionPrepareResponse prepareResponse =
                sessionPrepareService.prepareGameSession(userId, song, audioUrl, songGameData);

        // 5. Redis Context 업데이트
        conversationContextService.changeMode(userId, PlaybackMode.EXERCISE);
        conversationContextService.setCurrentSong(userId, songId);

        // 6. GameStartResponse 생성 (프론트엔드 기대 형식)
        com.heungbuja.game.dto.GameStartResponse gameResponse = com.heungbuja.game.dto.GameStartResponse.builder()
                .sessionId(prepareResponse.getSessionId())
                .songId(song.getId())
                .songTitle(song.getTitle())
                .songArtist(song.getArtist())
                .audioUrl(audioUrl)
                .videoUrls(generateVideoUrls())
                .bpm(songGameData.getBpm())
                .duration(songGameData.getDuration())
                .sectionInfo(songGameData.getSectionInfo())
                .lyricsInfo(songGameData.getLyricsInfo())
                .verse1Timeline(songGameData.getVerse1Timeline())
                .verse2Timelines(songGameData.getVerse2Timelines())
                .build();

        // 7. 프론트엔드에 전달할 데이터 구성
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("intent", "START_GAME_IMMEDIATELY");
        gameData.put("gameInfo", gameResponse); // GameStartResponse 전체를 담음

        // 8. GPT와 사용자에게 전달할 음성 메시지 생성
        String message = String.format("%s의 '%s' 노래로 체조를 시작합니다. 화면을 봐주세요.",
                song.getArtist(), song.getTitle());

        log.info("게임 시작 Tool 실행 완료: userId={}, sessionId={}, songId={}",
                userId, prepareResponse.getSessionId(), song.getId());

        // 7. McpToolResult 반환
        return McpToolResult.success(
                toolCall.getId(),
                "start_game",
                message,
                gameData
        );

//        User user = userService.findById(userId);
//        // 노래 선택
//        Song song;
//        if (songId != null) {
//            // 특정 노래로 게임 시작 (findById는 이미 예외를 던짐)
//            song = songService.findById(songId);
//        } else {
//            // songId가 없으면 랜덤 선택 또는 에러
//            // TODO: 안무 정보가 있는 노래만 선택하도록 개선 필요
//            throw new IllegalArgumentException("게임용 노래 ID가 필요합니다 (songId 파라미터)");
//        }
//
//        // 게임 시작 요청 생성
//        com.heungbuja.game.dto.GameStartRequest gameRequest =
//                new com.heungbuja.game.dto.GameStartRequest();
//        gameRequest.setUserId(userId);
//        gameRequest.setSongId(song.getId());
//
//        // 게임 서비스 호출 (1초 이내 반환)
//        com.heungbuja.game.dto.GameStartResponse gameResponse =
//                gameService.startGame(gameRequest);
//
//        // Redis Context 업데이트 (EXERCISE 모드로 전환)
//        conversationContextService.changeMode(userId, PlaybackMode.EXERCISE);
//        conversationContextService.setCurrentSong(userId, song.getId());
//
//        // 게임 정보를 data에 포함
//        Map<String, Object> gameData = new HashMap<>();
//        gameData.put("sessionId", gameResponse.getSessionId());
//        gameData.put("songId", song.getId());
//        gameData.put("songTitle", song.getTitle());
//        gameData.put("songArtist", song.getArtist());
//        gameData.put("audioUrl", gameResponse.getAudioUrl());
////        gameData.put("beatInfo", gameResponse.getBeatInfo());
////        gameData.put("choreographyInfo", gameResponse.getChoreographyInfo());
//        gameData.put("lyricsInfo", gameResponse.getLyricsInfo());
//
//        String message = String.format("%s의 '%s'로 게임을 시작할게요",
//                                       song.getArtist(), song.getTitle());
//
//        log.info("게임 시작 완료: userId={}, sessionId={}, songId={}",
//                 userId, gameResponse.getSessionId(), song.getId());
//
//        return McpToolResult.success(
//            toolCall.getId(),
//            "start_game",
//            message,
//            gameData
//        );
        // ----------------------- 수정 --------------------------------------------
    }

    /**
     * Tool: start_game_with_song
     * 특정 노래로 게임(체조) 시작 - 노래 검색 + 게임 시작을 한 번에 처리
     */
    private McpToolResult startGameWithSong(McpToolCall toolCall) {
        Map<String, Object> args = toolCall.getArguments();

        Long userId = getLongArg(args, "userId");
        String title = getStringArg(args, "title");
        String artist = getStringArg(args, "artist");

        // 빈 문자열을 null로 처리
        title = (title != null && title.trim().isEmpty()) ? null : title;
        artist = (artist != null && artist.trim().isEmpty()) ? null : artist;

        log.info("특정 노래로 게임 시작: userId={}, title={}, artist={}", userId, title, artist);

        try {
            // 1. 노래 검색
            Song song;
            if (artist != null && title != null) {
                song = songService.searchByArtistAndTitle(artist, title);
            } else if (artist != null) {
                song = songService.searchByArtist(artist);
            } else if (title != null) {
                song = songService.searchByTitle(title);
            } else {
                throw new IllegalArgumentException("노래 제목 또는 가수명이 필요합니다");
            }

            Long songId = song.getId();
            log.info("노래 검색 성공: songId={}, title={}, artist={}", songId, song.getTitle(), song.getArtist());

            // 2. Song 게임 데이터 조회 (MongoDB)
            com.heungbuja.song.dto.SongGameData songGameData =
                    songGameDataCache.getOrLoadSongGameData(songId);

            // 3. audioUrl 생성
            String audioUrl = mediaUrlService.issueUrlById(song.getMedia().getId());

            // 4. SessionPrepareService로 GameState 생성 (Redis 저장)
            com.heungbuja.game.dto.GameSessionPrepareResponse prepareResponse =
                    sessionPrepareService.prepareGameSession(userId, song, audioUrl, songGameData);

            // 5. Redis Context 업데이트
            conversationContextService.changeMode(userId, PlaybackMode.EXERCISE);
            conversationContextService.setCurrentSong(userId, songId);

            // 6. GameStartResponse 생성 (프론트엔드 기대 형식)
            com.heungbuja.game.dto.GameStartResponse gameResponse = com.heungbuja.game.dto.GameStartResponse.builder()
                    .sessionId(prepareResponse.getSessionId())
                    .songId(song.getId())
                    .songTitle(song.getTitle())
                    .songArtist(song.getArtist())
                    .audioUrl(audioUrl)
                    .videoUrls(generateVideoUrls())
                    .bpm(songGameData.getBpm())
                    .duration(songGameData.getDuration())
                    .sectionInfo(songGameData.getSectionInfo())
                    .lyricsInfo(songGameData.getLyricsInfo())
                    .verse1Timeline(songGameData.getVerse1Timeline())
                    .verse2Timelines(songGameData.getVerse2Timelines())
                    .build();

            // 7. 응답 데이터 구성
            Map<String, Object> gameData = new HashMap<>();
            gameData.put("intent", "START_GAME_IMMEDIATELY");
            gameData.put("gameInfo", gameResponse); // GameStartResponse 전체를 담음

            String message = String.format("%s의 '%s' 노래로 체조를 시작합니다. 화면을 봐주세요.",
                    song.getArtist(), song.getTitle());

            log.info("특정 노래로 게임 시작 완료: userId={}, sessionId={}, songId={}",
                    userId, prepareResponse.getSessionId(), songId);

            return McpToolResult.success(
                    toolCall.getId(),
                    "start_game_with_song",
                    message,
                    gameData
            );

        } catch (CustomException e) {
            log.error("특정 노래로 게임 시작 실패: {}", e.getMessage());
            return McpToolResult.failure(
                    toolCall.getId(),
                    toolCall.getName(),
                    e.getMessage()
            );
        }
    }

    // ========== 헬퍼 메서드 ==========

    private Long getLongArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String getStringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntegerArg(Map<String, Object> args, String key, Integer defaultValue) {
        Object value = args.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * 비디오 URL 생성 (SessionPrepareService와 동일한 로직)
     */
    private Map<String, String> generateVideoUrls() {
        Map<String, String> videoUrls = new HashMap<>();
        videoUrls.put("intro", "https://example.com/tutorial.mp4");
        videoUrls.put("verse1", "https://example.com/part1.mp4");
        videoUrls.put("verse2_level1", "https://example.com/part2_1.mp4");
        videoUrls.put("verse2_level2", "https://example.com/part2_2.mp4");
        videoUrls.put("verse2_level3", "https://example.com/part2_3.mp4");
        return videoUrls;
    }
}
