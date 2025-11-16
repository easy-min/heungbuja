package com.heungbuja.command.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heungbuja.command.dto.CommandRequest;
import com.heungbuja.command.dto.CommandResponse;
import com.heungbuja.performance.annotation.MeasurePerformance;
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
    private final com.heungbuja.session.service.SessionStateService sessionStateService;
    private final TtsService ttsService;
    private final VoiceCommandRepository voiceCommandRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @MeasurePerformance(component = "Command")
    @Transactional(noRollbackFor = {CustomException.class, Exception.class})
    public CommandResponse processTextCommand(CommandRequest request) {
        User user = userService.findById(request.getUserId());
        String text = request.getText().trim();

        log.info("[MCP] 명령 처리 시작: userId={}, text='{}'", user.getId(), text);

        try {
            // 0. 응급 상황 진행 중인지 먼저 체크
            boolean isEmergencyInProgress = sessionStateService.isEmergency(user.getId());

            if (isEmergencyInProgress) {
                // 응급 상황 진행 중일 때는 빈 입력이거나 애매한 입력이면 바로 안내 메시지 반환
                if (text.isEmpty() || text.isBlank() || text.length() < 2) {
                    log.info("⚠️ 응급 신고 진행 중 빈 입력 감지: userId={}, text='{}'", user.getId(), text);
                    return buildEmergencyInProgressResponse(user, text);
                }

                // 응급 키워드가 아니고, 괜찮아/안괜찮아도 아닌 애매한 입력인지 체크
                boolean isEmergencyKeyword = containsEmergencyKeyword(text);
                boolean isOkayResponse = containsOkayKeyword(text);
                boolean isNotOkayResponse = containsNotOkayKeyword(text);

                if (!isEmergencyKeyword && !isOkayResponse && !isNotOkayResponse) {
                    log.info("⚠️ 응급 신고 진행 중 애매한 입력: userId={}, text='{}'", user.getId(), text);
                    // GPT에게 툴 선택을 맡기지 않고 바로 안내 메시지 반환
                    return buildEmergencyInProgressResponse(user, text);
                }
            }

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
        // 응급 상황 진행 중인지 체크
        boolean isEmergencyInProgress = sessionStateService.isEmergency(userId);
        String emergencyWarning = "";

        if (isEmergencyInProgress) {
            log.warn("⚠️ 응급 신호 진행 중 명령 입력: userId={}, text='{}'", userId, userMessage);
            emergencyWarning = """

                    ⚠️⚠️⚠️ 매우 중요: 현재 응급 신고가 진행 중입니다! ⚠️⚠️⚠️

                    사용자가 "괜찮아"류 표현을 하면 반드시 cancel_emergency만 호출하세요:
                    - "괜찮아", "괜찮습니다", "괜찮아요", "아니야", "아니에요", "취소", "취소해" → cancel_emergency만!

                    사용자가 "안괜찮아"류 표현을 하면 반드시 confirm_emergency만 호출하세요:
                    - "안괜찮아", "안 괜찮아", "빨리 신고", "신고해", "위급해", "심각해" → confirm_emergency만!

                    사용자가 다시 응급 키워드를 말하면:
                    - "살려줘", "도와줘", "아파" 등 → handle_emergency (중복 신고 = 즉시 확정)

                    ⚠️ 주의: "괜찮아 XXX해줘" 같은 복합 명령도 cancel_emergency만 호출! XXX는 무시!

                    """;
        } else {
            log.debug("일반 상황 명령 입력: userId={}, text='{}'", userId, userMessage);
        }

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
                   - 설명: **최초** 응급 상황 감지 및 신고 생성
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * keyword (필수): 응급 키워드
                     * fullText (필수): 전체 발화 텍스트
                   - 사용 시점: "살려줘", "도와줘", "아파요" 등 응급 키워드를 **처음** 말할 때
                   - 참고: 응급 신고가 이미 진행 중이라면 즉시 확정됨

                6. cancel_emergency
                   - 설명: 진행 중인 응급 신고 취소
                   - 파라미터:
                     * userId (필수): 사용자 ID
                   - 사용 시점: 응급 신고 진행 중 사용자가 괜찮다고 응답할 때
                   - 인식 키워드: "괜찮아", "괜찮습니다", "괜찮아요", "괜찮네요", "아니야", "아니에요", "취소", "취소해", "잘못 눌렀어", "실수야", "실수였어"

                7. confirm_emergency
                   - 설명: **진행 중인** 응급 신고를 즉시 확정 (60초 대기 건너뛰기)
                   - 파라미터:
                     * userId (필수): 사용자 ID
                   - 사용 시점: 응급 신고 진행 중 "안 괜찮아", "안괜찮아", "빨리 신고해", "신고해", "위급해", "심각해" 등으로 응답할 때
                   - ⚠️ 주의: handle_emergency와 명확히 구분! confirm_emergency는 **이미 신고가 진행 중**일 때만 사용

                8. change_mode
                   - 설명: 모드 변경 (홈, 감상, 체조)
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * mode (필수): HOME, LISTENING, EXERCISE 중 하나

                9. start_game
                   - 설명: 게임(체조)을 시작합니다. 현재 선택된 노래 또는 랜덤 노래로 시작합니다.
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * songId: 게임에 사용할 노래 ID (선택적)

                10. start_game_with_song
                   - 설명: 특정 노래로 게임(체조)을 시작합니다. 노래 검색과 게임 시작을 한 번에 처리합니다.
                   - 파라미터:
                     * userId (필수): 사용자 ID
                     * title: 노래 제목
                     * artist: 가수명 (선택적)
                   - 사용 시점: 사용자가 "특정 노래로 체조/게임/운동"을 요청할 때
                """;

        return String.format("""
                당신은 노인을 위한 음성 인터페이스 AI입니다.
                사용자의 음성 명령을 분석하여 적절한 Tool을 선택하세요.
                %s
                [현재 상황]
                %s

                %s

                [사용자 명령]
                "%s"

                [명령 분석 절차]
                STEP 1: 사용자 명령에서 키워드 추출
                  - 노래 이름이 있는가? (가수명, 곡명 등)
                  - 체조/게임/운동 키워드가 있는가?
                  - 재생 키워드가 있는가? (틀어/들려/듣고)

                STEP 2: 패턴 결정
                  ⚠️⚠️⚠️ 매우 중요: 하나의 명령에는 반드시 하나의 Tool만 호출! ⚠️⚠️⚠️
                  ⚠️ 특히 응급 상황에서는 절대 여러 Tool 호출 금지!

                  - 패턴 A (노래로 체조): 노래 이름 + "체조/게임/운동" → start_game_with_song (한 번에!)
                  - 패턴 B (노래만 듣기): 노래 이름 + "틀어/들려/듣고" → search_song만
                  - 패턴 C (랜덤 체조): "체조/게임/운동"만 → start_game만
                  - 패턴 D (응급 취소): "괜찮아" 포함 → cancel_emergency만! (뒤에 다른 말이 있어도 무시!)
                  - 패턴 E (응급 확정): "안괜찮아" 포함 → confirm_emergency만!

                STEP 3: Tool 호출 생성
                  - 패턴에 맞는 Tool 정확히 하나만 호출
                  - 응급 상황에서 "괜찮아 XXX해줘"는 cancel_emergency만 호출! XXX 무시!

                [패턴 A 예시: 노래로 체조 - start_game_with_song 사용]
                "당돌한 여자로 체조하고 싶어" → start_game_with_song(title="당돌한 여자")
                "당돌한 여자로 게임해줘" → start_game_with_song(title="당돌한 여자")
                "서주경의 당돌한 여자로 운동할래" → start_game_with_song(artist="서주경", title="당돌한 여자")

                [패턴 B 예시: 노래만 듣기 - search_song 사용]
                "당돌한 여자 틀어줘" → search_song(title="당돌한 여자")
                "당돌한 여자 들려줘" → search_song(title="당돌한 여자")
                "당돌한 여자 듣고 싶어" → search_song(title="당돌한 여자")

                [패턴 C 예시: 랜덤 체조 - start_game 사용]
                "체조하고 싶어" → start_game()
                "게임할래" → start_game()

                [패턴 D 예시: 응급 상황 처리]
                ⚠️ 응급 Tool 구분 규칙:
                - handle_emergency: 처음 응급 키워드를 말할 때 (신고 생성)
                - cancel_emergency: 신고 진행 중 취소 의사를 밝힐 때
                - confirm_emergency: 신고 진행 중 확정 의사를 밝힐 때

                시나리오 1 (최초 응급 신고):
                "살려줘" → handle_emergency(keyword="살려줘", fullText="살려줘")
                "아파요 도와주세요" → handle_emergency(keyword="아파요", fullText="아파요 도와주세요")

                시나리오 2 (신고 진행 중 - 취소):
                (이미 신고 진행 중) "괜찮아" → cancel_emergency()
                (이미 신고 진행 중) "괜찮습니다" → cancel_emergency()
                (이미 신고 진행 중) "괜찮아요" → cancel_emergency()
                (이미 신고 진행 중) "아니야" → cancel_emergency()
                (이미 신고 진행 중) "취소해" → cancel_emergency()
                (이미 신고 진행 중) "잘못 눌렀어" → cancel_emergency()

                시나리오 3 (신고 진행 중 - 즉시 확정):
                (이미 신고 진행 중) "안괜찮아" → confirm_emergency()
                (이미 신고 진행 중) "빨리 신고해" → confirm_emergency()
                (이미 신고 진행 중) "신고해줘" → confirm_emergency()

                시나리오 4 (신고 진행 중 - 중복 응급 키워드 = 즉시 확정):
                (이미 신고 진행 중) "도와줘" → handle_emergency(keyword="도와줘", fullText="도와줘")
                → 시스템이 자동으로 기존 신고를 즉시 확정 처리

                [JSON 예시]
                입력: "당돌한 여자로 체조하고 싶어"
                응답:
                {
                  "tool_calls": [
                    {"name": "start_game_with_song", "arguments": {"userId": 1, "title": "당돌한 여자"}}
                  ]
                }

                입력: "당돌한 여자 들려줘"
                응답:
                {
                  "tool_calls": [
                    {"name": "search_song", "arguments": {"userId": 1, "title": "당돌한 여자"}}
                  ]
                }

                입력: "체조하고 싶어"
                응답:
                {
                  "tool_calls": [
                    {"name": "start_game", "arguments": {"userId": 1}}
                  ]
                }

                입력: "살려줘" (최초 응급 신고)
                응답:
                {
                  "tool_calls": [
                    {"name": "handle_emergency", "arguments": {"userId": 1, "keyword": "살려줘", "fullText": "살려줘"}}
                  ]
                }

                입력: "안괜찮아" (신고 진행 중)
                응답:
                {
                  "tool_calls": [
                    {"name": "confirm_emergency", "arguments": {"userId": 1}}
                  ]
                }

                입력: "괜찮아" (신고 진행 중)
                응답:
                {
                  "tool_calls": [
                    {"name": "cancel_emergency", "arguments": {"userId": 1}}
                  ]
                }

                입력: "괜찮습니다" (신고 진행 중)
                응답:
                {
                  "tool_calls": [
                    {"name": "cancel_emergency", "arguments": {"userId": 1}}
                  ]
                }

                입력: "아니야" (신고 진행 중)
                응답:
                {
                  "tool_calls": [
                    {"name": "cancel_emergency", "arguments": {"userId": 1}}
                  ]
                }

                입력: "괜찮아 체조해줘" (신고 진행 중 - 복합 명령)
                응답:
                {
                  "tool_calls": [
                    {"name": "cancel_emergency", "arguments": {"userId": 1}}
                  ]
                }
                ⚠️ 주의: "체조해줘"는 무시하고 cancel_emergency만 호출!

                입력: "괜찮아요 노래 틀어줘" (신고 진행 중 - 복합 명령)
                응답:
                {
                  "tool_calls": [
                    {"name": "cancel_emergency", "arguments": {"userId": 1}}
                  ]
                }
                ⚠️ 주의: "노래 틀어줘"는 무시하고 cancel_emergency만 호출!

                [응답 형식]
                - userId는 %d로 설정
                - 반드시 JSON만 출력 (설명 금지)
                - tool_calls는 배열이지만 대부분 하나의 Tool만 호출

                JSON만 출력:
                """, emergencyWarning, contextInfo, toolsDescription, userMessage, userId);
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
        // 응급 상황 진행 중인지 체크
        boolean isEmergencyInProgress = sessionStateService.isEmergency(user.getId());

        if (isEmergencyInProgress) {
            // 응급 신고 진행 중일 때는 상태 안내
            log.info("응급 신고 진행 중 애매한 응답: userId={}, text='{}'", user.getId(), text);

            String responseText = "신고가 진행되고 있습니다. 괜찮으시면 '괜찮아'라고, 정말 위급하시면 '안 괜찮아'라고 말씀해주세요";

            saveVoiceCommand(user, text, Intent.EMERGENCY);

            return CommandResponse.builder()
                    .success(true)
                    .intent(Intent.EMERGENCY)  // 응급 상황 유지
                    .responseText(responseText)
                    .ttsAudioUrl(null)  // TTS는 Controller에서 처리
                    .build();
        }

        // 일반 상황
        String prompt = String.format("""
                사용자 요청: "%s"

                위 요청에 대해 간단히 답변하세요.

                [중요 제약사항]
                - 반드시 1문장으로만 답변하세요
                - 10단어 이내로 짧게 답변하세요
                - 어르신이 이해하기 쉽게 답변하세요
                - "죄송합니다. 이해하지 못했습니다" 또는 "다시 말씀해주세요" 형태로만 답변하세요
                - "유료 광고", "시청해주셔서 감사합니다", "구독", "좋아요" 같은 문구는 절대 사용하지 마세요
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
        log.debug("buildResponse 시작: toolResults 개수={}", toolResults.size());
        for (int i = toolResults.size() - 1; i >= 0; i--) {
            McpToolResult result = toolResults.get(i);
            log.debug("Tool 결과 처리 중 [{}]: toolName={}, success={}", i, result.getToolName(), result.isSuccess());
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

            // start_game_with_song: 특정 노래로 게임 시작 → EXERCISE 모드로 화면 전환
            if ("start_game_with_song".equals(result.getToolName()) && result.getData() != null) {
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
                log.info("✅ cancel_emergency 처리: responseText='{}'", responseText);
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
        log.warn("⚠️ 매칭되는 Tool이 없음 - Intent.UNKNOWN 반환: responseText='{}'", responseText);
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

    /**
     * 응급 상황 진행 중 안내 메시지 반환
     */
    private CommandResponse buildEmergencyInProgressResponse(User user, String text) {
        String responseText = "신고가 진행되고 있습니다. 괜찮으시면 '괜찮아'라고, 정말 위급하시면 '안 괜찮아'라고 말씀해주세요";

        saveVoiceCommand(user, text, Intent.EMERGENCY);

        return CommandResponse.builder()
                .success(true)
                .intent(Intent.EMERGENCY)
                .responseText(responseText)
                .ttsAudioUrl(null)
                .build();
    }

    /**
     * 응급 키워드 포함 여부 체크
     */
    private boolean containsEmergencyKeyword(String text) {
        String normalized = text.toLowerCase().replaceAll("\\s+", "");
        String[] emergencyKeywords = {
            "도와줘", "도와주세요", "살려줘", "살려주세요",
            "아야", "아파", "쓰러졌어", "위험해"
        };

        for (String keyword : emergencyKeywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * "괜찮아" 관련 키워드 포함 여부 체크
     */
    private boolean containsOkayKeyword(String text) {
        String normalized = text.toLowerCase().replaceAll("\\s+", "");
        String[] okayKeywords = {
            "괜찮아", "괜찮습니다", "괜찮아요", "괜찮네요",
            "아니야", "아니에요", "취소", "취소해",
            "잘못", "실수"
        };

        for (String keyword : okayKeywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * "안괜찮아" 관련 키워드 포함 여부 체크
     */
    private boolean containsNotOkayKeyword(String text) {
        String normalized = text.toLowerCase().replaceAll("\\s+", "");
        String[] notOkayKeywords = {
            "안괜찮아", "안괜찮", "빨리", "신고해", "신고",
            "위급해", "위급", "심각해", "심각"
        };

        for (String keyword : notOkayKeywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

}
