package com.heungbuja.command.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heungbuja.command.dto.IntentResult;
import com.heungbuja.command.service.IntentClassifier;
import com.heungbuja.gpt.service.GptService;
import com.heungbuja.voice.enums.Intent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RAG 기반 의도 분석기 (GPT-5-nano 사용)
 *
 * GptService를 활용하여 컨텍스트 기반 Intent 분석 수행
 * - 대화 컨텍스트를 고려한 의도 파악
 * - 유연한 자연어 이해
 * - JSON 형식의 구조화된 응답 파싱
 */
@Slf4j
@Component
@Primary  // KeywordBasedIntentClassifier 대신 이 구현체를 기본으로 사용
@RequiredArgsConstructor
public class RagBasedIntentClassifier implements IntentClassifier {

    private final GptService gptService;
    private final com.heungbuja.context.service.ConversationContextService conversationContextService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public IntentResult classify(String text, Long userId) {
        try {
            // Redis에서 대화 컨텍스트 가져오기
            String contextInfo = buildContextInfo(userId);

            // 🚀 GPT API 호출하여 Intent 분석 (최적화 버전 사용!)
            String jsonResponse = gptService.analyzeIntentOptimized(text, contextInfo);

            log.debug("GPT Intent 분석 응답 (최적화): {}", jsonResponse);

            // JSON 응답 파싱
            return parseGptResponse(jsonResponse, text);

        } catch (Exception e) {
            log.error("Intent 분석 실패 (최적화): text={}", text, e);

            // 실패 시 UNKNOWN Intent 반환
            return IntentResult.builder()
                    .intent(Intent.UNKNOWN)
                    .rawText(text)
                    .confidence(0.0)
                    .build();
        }
    }

    @Override
    public String getClassifierType() {
        return "RAG_GPT_OPTIMIZED";
    }

    /**
     * 대화 컨텍스트 정보 구성
     *
     * Redis에서 다음 정보를 가져옴:
     * - 현재 모드 (HOME, LISTENING, EXERCISE)
     * - 재생 중인 곡 정보
     * - 대기열 정보
     * - 마지막 상호작용 시각
     */
    private String buildContextInfo(Long userId) {
        if (userId == null) {
            return "컨텍스트 정보 없음 (사용자 ID 없음)";
        }

        try {
            return conversationContextService.formatContextForGpt(userId);
        } catch (Exception e) {
            log.warn("컨텍스트 조회 실패, 기본값 사용: userId={}", userId, e);
            return "컨텍스트 정보 없음 (조회 실패)";
        }
    }

    /**
     * GPT 응답 JSON 파싱
     *
     * 예상 형식:
     * {
     *   "intent": "SELECT_BY_ARTIST",
     *   "entities": {
     *     "artist": "태진아",
     *     "title": "좋은 날"
     *   },
     *   "confidence": 0.95,
     *   "reasoning": "사용자가 태진아의 좋은 날을 요청했습니다"
     * }
     */
    private IntentResult parseGptResponse(String jsonResponse, String rawText) {
        try {
            // JSON을 Map으로 파싱
            Map<String, Object> response = objectMapper.readValue(
                    jsonResponse,
                    new TypeReference<Map<String, Object>>() {}
            );

            // Intent 추출
            String intentStr = (String) response.get("intent");
            Intent intent = parseIntent(intentStr);

            // Confidence 추출
            Double confidence = response.containsKey("confidence")
                    ? ((Number) response.get("confidence")).doubleValue()
                    : 1.0;

            // Entities 추출
            @SuppressWarnings("unchecked")
            Map<String, String> entities = response.containsKey("entities")
                    ? (Map<String, String>) response.get("entities")
                    : Map.of();

            log.info("Intent 분석 완료: intent={}, confidence={}, entities={}",
                    intent, confidence, entities);

            return IntentResult.builder()
                    .intent(intent)
                    .entities(entities)
                    .confidence(confidence)
                    .rawText(rawText)
                    .build();

        } catch (Exception e) {
            log.error("GPT 응답 파싱 실패: response={}", jsonResponse, e);

            // 파싱 실패 시 UNKNOWN Intent 반환
            return IntentResult.builder()
                    .intent(Intent.UNKNOWN)
                    .rawText(rawText)
                    .confidence(0.0)
                    .build();
        }
    }

    /**
     * Intent 문자열을 Enum으로 변환
     */
    private Intent parseIntent(String intentStr) {
        if (intentStr == null || intentStr.isBlank()) {
            return Intent.UNKNOWN;
        }

        try {
            return Intent.valueOf(intentStr);
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 Intent: {}", intentStr);
            return Intent.UNKNOWN;
        }
    }
}
