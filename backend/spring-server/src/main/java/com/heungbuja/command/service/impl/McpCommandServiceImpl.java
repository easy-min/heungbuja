package com.heungbuja.command.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heungbuja.command.dto.CommandRequest;
import com.heungbuja.command.dto.CommandResponse;
import com.heungbuja.command.mcp.McpToolService;
import com.heungbuja.command.mcp.dto.McpToolCall;
import com.heungbuja.command.mcp.dto.McpToolDefinition;
import com.heungbuja.command.mcp.dto.McpToolResult;
import com.heungbuja.command.service.CommandService;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.context.service.ConversationContextService;
import com.heungbuja.gpt.dto.GptMessage;
import com.heungbuja.gpt.service.GptService;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.service.UserService;
import com.heungbuja.voice.entity.VoiceCommand;
import com.heungbuja.voice.enums.Intent;
import com.heungbuja.voice.repository.VoiceCommandRepository;
import com.heungbuja.voice.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 기반 명령 처리 서비스 구현체
 *
 * 기존 CommandServiceImpl과 달리 switch문 없이 GPT가 직접 Tool을 선택하고 호출합니다.
 *
 * @Primary 애노테이션으로 이 구현체가 기본으로 사용됩니다.
 * 기존 CommandServiceImpl로 돌아가려면 이 애노테이션을 제거하세요.
 */
@Slf4j
@Service
@Primary  // 이 구현체를 기본으로 사용 (제거하면 CommandServiceImpl 사용)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class McpCommandServiceImpl implements CommandService {

    private final UserService userService;
    private final GptService gptService;
    private final McpToolService mcpToolService;
    private final ConversationContextService conversationContextService;
    private final TtsService ttsService;
    private final VoiceCommandRepository voiceCommandRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(noRollbackFor = {CustomException.class, Exception.class})
    public CommandResponse processTextCommand(CommandRequest request) {
        User user = userService.findById(request.getUserId());
        String text = request.getText().trim();

        log.info("[MCP] 명령 처리 시작: userId={}, text='{}'", user.getId(), text);

        try {
            // 1. Redis에서 대화 컨텍스트 조회
            String contextInfo = conversationContextService.formatContextForGpt(user.getId());

            // 2. GPT에게 Tools + Context 전달하여 Tool 호출 요청
            List<McpToolCall> toolCalls = requestGptWithTools(text, contextInfo, user.getId());

            if (toolCalls.isEmpty()) {
                // Tool 호출 없이 GPT가 직접 응답한 경우
                log.warn("[MCP] GPT가 Tool을 호출하지 않음: text='{}'", text);
                return handleDirectGptResponse(user, text);
            }

            // 3. Tool 실행
            List<McpToolResult> toolResults = executeTools(toolCalls);

            // 4. Tool 결과를 GPT에게 전달하여 최종 응답 생성
            String finalResponse = generateFinalResponse(text, contextInfo, toolCalls, toolResults);

            // 5. 음성 명령 로그 저장
            saveVoiceCommand(user, text, Intent.UNKNOWN); // MCP에서는 Intent가 불명확

            // 6. 응답 생성 (TTS는 Controller에서 synthesizeBytes()로 직접 처리)
            return buildResponse(finalResponse, null, toolResults);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[MCP] 명령 처리 실패: userId={}, text='{}'", user.getId(), text, e);
            throw new CustomException(ErrorCode.COMMAND_EXECUTION_FAILED, "명령 처리 중 오류가 발생했습니다");
        }
    }

    /**
     * GPT에게 Tools를 제공하고 Tool 호출 요청
     */
    private List<McpToolCall> requestGptWithTools(String userMessage, String contextInfo, Long userId) {
        log.debug("[MCP] GPT에게 Tools 전달: message='{}'", userMessage);

        return parseToolCallsFromGptResponse(userMessage, contextInfo, userId);
    }

    /**
     * GPT에게 어떤 Tool을 호출해야 하는지 물어보고 JSON 응답 파싱
     */
    private List<McpToolCall> parseToolCallsFromGptResponse(String userMessage, String contextInfo, Long userId) {
        // GPT에게 Tool 선택을 요청하는 프롬프트
        String toolSelectionPrompt = buildToolSelectionPrompt(userMessage, contextInfo, userId);

        log.debug("[MCP] GPT에게 Tool 선택 요청");

        // GPT 호출
        var gptResponse = gptService.chat(toolSelectionPrompt);

        if (gptResponse == null || gptResponse.getContent() == null) {
            log.warn("[MCP] GPT 응답 없음");
            return List.of();
        }

        String jsonResponse = gptResponse.getContent();
        log.debug("[MCP] GPT Tool 선택 응답: {}", jsonResponse);

        // JSON 파싱
        try {
            // JSON에서 tool_calls 배열 추출
            Map<String, Object> responseMap = objectMapper.readValue(
                    jsonResponse,
                    new TypeReference<Map<String, Object>>() {}
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCallsData =
                    (List<Map<String, Object>>) responseMap.get("tool_calls");

            if (toolCallsData == null || toolCallsData.isEmpty()) {
                log.info("[MCP] Tool 호출 없음");
                return List.of();
            }

            // McpToolCall 객체로 변환
            List<McpToolCall> toolCalls = new ArrayList<>();
            for (Map<String, Object> toolCallData : toolCallsData) {
                String name = (String) toolCallData.get("name");
                @SuppressWarnings("unchecked")
                Map<String, Object> arguments = (Map<String, Object>) toolCallData.get("arguments");

                toolCalls.add(McpToolCall.builder()
                        .id("call_" + System.currentTimeMillis() + "_" + toolCalls.size())
                        .name(name)
                        .arguments(arguments != null ? arguments : Map.of())
                        .build());

                log.info("[MCP] Tool 호출 파싱 완료: name={}, args={}", name, arguments);
            }

            return toolCalls;

        } catch (Exception e) {
            log.error("[MCP] GPT 응답 파싱 실패: response={}", jsonResponse, e);
            return List.of();
        }
    }

    /**
     * Tool 선택을 위한 프롬프트 생성
     */
    private String buildToolSelectionPrompt(String userMessage, String contextInfo, Long userId) {
        // Tools 설명
        String toolsDescription = """
                [사용 가능한 Tools]

                1. search_song
                   - 설명: 가수명, 제목, 연대, 장르, 분위기로 노래 검색
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * artist: 가수명
                     * title: 곡명
                     * era: 연대 (1980s, 1990s 등)
                     * genre: 장르 (발라드, 댄스, 트로트 등)
                     * mood: 분위기 (슬픈, 경쾌한 등)
                     * excludeSongId: 제외할 곡 ID

                2. control_playback
                   - 설명: 재생 제어 (일시정지, 재생, 다음곡, 정지)
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * action (필수): PAUSE, RESUME, NEXT, STOP 중 하나

                3. add_to_queue
                   - 설명: 대기열에 곡 추가
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * artist (필수): 가수명
                     * count: 추가할 곡 개수 (기본값: 1)

                4. get_current_context
                   - 설명: 현재 재생 상태, 대기열 정보 조회
                   - 파라미터:
                     * userId (필수): 사용자 ID

                5. handle_emergency
                   - 설명: 응급 상황 감지 및 신고
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * keyword (필수): 응급 키워드
                     * fullText (필수): 전체 발화 텍스트

                6. cancel_emergency
                   - 설명: 응급 신고 취소 (사용자가 "괜찮아", "괜찮아요" 등으로 응답할 때)
                   - 파라미터:
                     * userId (필수): 사용자 ID

                7. confirm_emergency
                   - 설명: 응급 신고 즉시 확정 (사용자가 "안 괜찮아", "빨리 신고해" 등으로 응답할 때)
                   - 파라미터:
                     * userId (필수): 사용자 ID

                8. change_mode
                   - 설명: 모드 변경 (홈, 감상, 체조)
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * mode (필수): HOME, LISTENING, EXERCISE 중 하나

                9. start_game
                   - 설명: 게임(체조)을 시작합니다. 노래에 맞춰 동작을 따라하는 3-5분 게임입니다.
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * songId: 게임에 사용할 노래 ID (선택적, 안무 정보가 있는 노래만 가능)
                """;

        return String.format("""
                당신은 노인을 위한 음성 인터페이스 AI입니다.
                사용자의 음성 명령을 분석하여 적절한 Tool을 선택하세요.

                [현재 상황]
                %s

                %s

                [사용자 명령]
                "%s"

                [핵심 규칙 - 반드시 준수]
                1. "체조", "게임", "운동" 키워드가 있으면 무조건 start_game 호출
                2. 특정 노래로 체조하려면 search_song + start_game 둘 다 호출 (순서대로)
                3. 여러 Tool을 호출할 때는 tool_calls 배열에 모두 포함

                [예시]
                입력: "당돌한 여자로 체조해"
                응답:
                {
                  "tool_calls": [
                    {"name": "search_song", "arguments": {"userId": 1, "title": "당돌한 여자"}},
                    {"name": "start_game", "arguments": {"userId": 1}}
                  ]
                }
                참고: search_song 후 start_game 호출 시, songId는 생략하면 자동으로 검색된 노래 사용

                입력: "체조하고 싶어"
                응답:
                {
                  "tool_calls": [
                    {"name": "start_game", "arguments": {"userId": 1}}
                  ]
                }

                입력: "노래 틀어줘"
                응답:
                {
                  "tool_calls": [
                    {"name": "search_song", "arguments": {"userId": 1}}
                  ]
                }

                [응답 형식]
                - userId는 %d로 설정
                - 반드시 JSON만 출력 (설명 금지)
                - tool_calls는 배열 (여러 개 가능)

                JSON만 출력:
                """, contextInfo, toolsDescription, userMessage, userId);
    }

    /**
     * Tool 실행
     */
    private List<McpToolResult> executeTools(List<McpToolCall> toolCalls) {
        List<McpToolResult> results = new ArrayList<>();

        for (McpToolCall toolCall : toolCalls) {
            log.info("[MCP] Tool 실행: name={}", toolCall.getName());
            McpToolResult result = mcpToolService.executeTool(toolCall);
            results.add(result);
        }

        return results;
    }

    /**
     * Tool 결과를 기반으로 GPT가 최종 응답 생성
     */
    private String generateFinalResponse(String originalMessage, String contextInfo,
                                          List<McpToolCall> toolCalls, List<McpToolResult> toolResults) {

        // Tool 결과를 텍스트로 포맷팅
        StringBuilder toolResultsText = new StringBuilder();
        for (int i = 0; i < toolResults.size(); i++) {
            McpToolResult result = toolResults.get(i);
            toolResultsText.append(String.format("Tool %d (%s): %s\n",
                    i + 1, result.getToolName(), result.getMessage()));
        }

        // GPT에게 최종 응답 요청
        String prompt = String.format("""
                사용자 요청: "%s"

                Tool 실행 결과:
                %s

                위 결과를 바탕으로 사용자에게 자연스러운 응답을 생성하세요.

                [중요 제약사항]
                - 반드시 1-2문장으로만 답변하세요
                - 15단어 이내로 짧게 답변하세요
                - "~했어요", "~할게요", "~드릴게요" 등 간단한 종결어미 사용
                - 어르신이 듣기 편하도록 핵심만 전달하세요
                """, originalMessage, toolResultsText);

        var gptResponse = gptService.chat(prompt);

        return gptResponse != null && gptResponse.getContent() != null
                ? gptResponse.getContent()
                : "처리했어요";
    }

    /**
     * Tool을 호출하지 않고 GPT가 직접 응답한 경우
     */
    private CommandResponse handleDirectGptResponse(User user, String text) {
        String prompt = String.format("""
                사용자 요청: "%s"

                위 요청에 대해 간단히 답변하세요.

                [중요 제약사항]
                - 반드시 1문장으로만 답변하세요
                - 10단어 이내로 짧게 답변하세요
                - 어르신이 이해하기 쉽게 답변하세요
                """, text);

        var gptResponse = gptService.chat(prompt);
        String responseText = gptResponse != null && gptResponse.getContent() != null
                ? gptResponse.getContent()
                : "죄송합니다. 이해하지 못했습니다";

        saveVoiceCommand(user, text, Intent.UNKNOWN);

        return CommandResponse.builder()
                .success(false)
                .intent(Intent.UNKNOWN)
                .responseText(responseText)
                .ttsAudioUrl(null)  // TTS는 Controller에서 처리
                .build();
    }

    /**
     * 응답 생성
     * ttsUrl은 사용하지 않음 (Controller에서 synthesizeBytes()로 직접 처리)
     *
     * 중요: 마지막 Tool 결과를 우선 처리하기 위해 역순으로 순회합니다.
     * 예: search_song → start_game 호출 시, start_game 결과가 우선 처리됩니다.
     */
    private CommandResponse buildResponse(String responseText, String ttsUrl, List<McpToolResult> toolResults) {
        // Tool 결과에 따라 응답 생성 (역순 순회: 마지막 Tool 우선 처리)
        for (int i = toolResults.size() - 1; i >= 0; i--) {
            McpToolResult result = toolResults.get(i);
            // search_song: 노래 재생 → LISTENING 모드로 화면 전환
            if ("search_song".equals(result.getToolName()) && result.getSongInfo() != null) {
                return CommandResponse.builder()
                        .success(true)
                        .intent(Intent.SELECT_BY_ARTIST)  // ✅ 노래 검색 Intent
                        .responseText(responseText)
                        .ttsAudioUrl(null)  // TTS는 Controller에서 처리
                        .songInfo(result.getSongInfo())
                        .screenTransition(CommandResponse.ScreenTransition.builder()
                                .targetScreen("/listening")
                                .action("PLAY_SONG")
                                .data(Map.of(
                                    "songId", result.getSongInfo().getSongId(),
                                    "autoPlay", true
                                ))
                                .build())
                        .build();
            }

            // start_game: 게임 시작 → EXERCISE 모드로 화면 전환
            if ("start_game".equals(result.getToolName()) && result.getData() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> gameData = (Map<String, Object>) result.getData();

                return CommandResponse.builder()
                        .success(true)
                        .intent(Intent.MODE_EXERCISE)  // ✅ 게임 시작 Intent
                        .responseText(responseText)
                        .ttsAudioUrl(null)  // TTS는 Controller에서 처리
                        .screenTransition(CommandResponse.ScreenTransition.builder()
                                .targetScreen("/game")
                                .action("START_GAME")
                                .data(gameData)  // sessionId, audioUrl, beatInfo 등 포함
                                .build())
                        .build();
            }

            // control_playback: 재생 제어
            if ("control_playback".equals(result.getToolName())) {
                Intent playbackIntent = mapPlaybackActionToIntent(result.getData());
                return CommandResponse.builder()
                        .success(true)
                        .intent(playbackIntent)  // ✅ MUSIC_PAUSE, MUSIC_RESUME 등
                        .responseText(responseText)
                        .ttsAudioUrl(null)
                        .build();
            }

            // handle_emergency: 응급 상황
            if ("handle_emergency".equals(result.getToolName())) {
                return CommandResponse.builder()
                        .success(true)
                        .intent(Intent.EMERGENCY)  // ✅ 응급 Intent
                        .responseText(responseText)
                        .ttsAudioUrl(null)
                        .build();
            }

            // cancel_emergency: 응급 취소
            if ("cancel_emergency".equals(result.getToolName())) {
                return CommandResponse.builder()
                        .success(true)
                        .intent(Intent.EMERGENCY_CANCEL)  // ✅ 응급 취소 Intent
                        .responseText(responseText)
                        .ttsAudioUrl(null)
                        .build();
            }

            // confirm_emergency: 응급 즉시 확정
            if ("confirm_emergency".equals(result.getToolName())) {
                return CommandResponse.builder()
                        .success(true)
                        .intent(Intent.EMERGENCY_CONFIRM)  // ✅ 응급 확정 Intent
                        .responseText(responseText)
                        .ttsAudioUrl(null)
                        .build();
            }

            // change_mode: 모드 전환
            if ("change_mode".equals(result.getToolName())) {
                Intent modeIntent = mapModeToIntent(result.getData());
                return CommandResponse.builder()
                        .success(true)
                        .intent(modeIntent)  // ✅ MODE_HOME, MODE_LISTENING, MODE_EXERCISE
                        .responseText(responseText)
                        .ttsAudioUrl(null)
                        .screenTransition(buildModeTransition(modeIntent))
                        .build();
            }
        }

        // 일반 응답 (화면 전환 없음)
        return CommandResponse.builder()
                .success(true)
                .intent(Intent.UNKNOWN)
                .responseText(responseText)
                .ttsAudioUrl(null)  // TTS는 Controller에서 처리
                .build();
    }

    /**
     * Playback action을 Intent로 매핑
     */
    private Intent mapPlaybackActionToIntent(Object data) {
        if (data instanceof Map) {
            String action = (String) ((Map<?, ?>) data).get("action");
            if (action != null) {
                return switch (action.toUpperCase()) {
                    case "PAUSE" -> Intent.MUSIC_PAUSE;
                    case "RESUME" -> Intent.MUSIC_RESUME;
                    case "NEXT" -> Intent.MUSIC_NEXT;
                    case "STOP" -> Intent.MUSIC_STOP;
                    default -> Intent.UNKNOWN;
                };
            }
        }
        return Intent.UNKNOWN;
    }

    /**
     * Mode를 Intent로 매핑
     */
    private Intent mapModeToIntent(Object data) {
        if (data instanceof Map) {
            String mode = (String) ((Map<?, ?>) data).get("mode");
            if (mode != null) {
                return switch (mode.toUpperCase()) {
                    case "HOME" -> Intent.MODE_HOME;
                    case "LISTENING" -> Intent.MODE_LISTENING;
                    case "EXERCISE" -> Intent.MODE_EXERCISE;
                    default -> Intent.UNKNOWN;
                };
            }
        }
        return Intent.UNKNOWN;
    }

    /**
     * Mode에 따른 화면 전환 생성
     */
    private CommandResponse.ScreenTransition buildModeTransition(Intent modeIntent) {
        return switch (modeIntent) {
            case MODE_HOME -> CommandResponse.ScreenTransition.builder()
                    .targetScreen("/home")
                    .action("GO_HOME")
                    .data(Map.of())
                    .build();
            case MODE_LISTENING -> CommandResponse.ScreenTransition.builder()
                    .targetScreen("/listening")
                    .action("GO_LISTENING")
                    .data(Map.of())
                    .build();
            case MODE_EXERCISE -> CommandResponse.ScreenTransition.builder()
                    .targetScreen("/exercise")
                    .action("GO_EXERCISE")
                    .data(Map.of())
                    .build();
            default -> null;
        };
    }

    /**
     * System Prompt 생성
     */
    private String buildSystemPrompt(String contextInfo) {
        return String.format("""
                당신은 노인을 위한 음성 인터페이스 AI입니다.
                사용자의 음성 명령을 이해하고 적절한 Tool을 호출하여 처리합니다.

                [현재 상황]
                %s

                [사용 가능한 Tools]
                - search_song: 노래 검색
                - control_playback: 재생 제어
                - add_to_queue: 대기열 추가
                - get_current_context: 현재 상태 조회
                - handle_emergency: 응급 상황
                - change_mode: 모드 변경

                [지침]
                - 사용자 요청에 맞는 Tool을 선택하여 호출하세요
                - 복잡한 요청은 여러 Tool을 순차적으로 호출하세요
                - 어르신이 이해하기 쉽게 짧고 명확하게 응답하세요
                """, contextInfo);
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

}
