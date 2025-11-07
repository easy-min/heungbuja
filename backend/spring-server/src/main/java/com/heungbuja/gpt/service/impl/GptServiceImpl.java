package com.heungbuja.gpt.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.gpt.dto.GptMessage;
import com.heungbuja.gpt.dto.GptRequest;
import com.heungbuja.gpt.dto.GptResponse;
import com.heungbuja.gpt.service.GptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * GPT API 서비스 구현체 (gpt-5-nano)
 * GMS 호환을 위해 순수 HttpClient 사용
 */
@Slf4j
@Service
public class GptServiceImpl implements GptService {

    @Value("${gpt.api.url}")
    private String apiUrl;

    @Value("${gpt.api.key}")
    private String apiKey;

    @Value("${gpt.model:gpt-5-nano}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GptServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public GptResponse chat(List<GptMessage> messages) {
        try {
            GptRequest request = GptRequest.builder()
                    .model(model)
                    .messages(messages)
                    .build();

            // JSON 변환
            String requestBody = objectMapper.writeValueAsString(request);

            log.debug("GPT API 호출: model={}, messages={}", model, messages.size());

            // HTTP 요청 생성
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "HeungbujaApp/1.0")
                    .header("Accept", "*/*")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // API 호출
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                GptResponse gptResponse = objectMapper.readValue(response.body(), GptResponse.class);

                if (gptResponse != null && gptResponse.getUsage() != null) {
                    log.info("GPT API 응답: tokens={}", gptResponse.getUsage().getTotalTokens());
                }

                return gptResponse;
            } else {
                log.error("GPT API 응답 오류: Status={}, Body={}", response.statusCode(), response.body());
                throw new CustomException(ErrorCode.EXTERNAL_API_ERROR,
                        "GPT API 응답 오류: " + response.statusCode());
            }

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("GPT API 호출 실패", e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "GPT API 호출 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Override
    public GptResponse chat(String userMessage) {
        List<GptMessage> messages = Arrays.asList(
                GptMessage.developer("Answer in Korean"),
                GptMessage.user(userMessage)
        );
        return chat(messages);
    }

    @Override
    public GptResponse chat(String systemPrompt, String userMessage) {
        List<GptMessage> messages = Arrays.asList(
                GptMessage.system(systemPrompt),
                GptMessage.user(userMessage)
        );
        return chat(messages);
    }

    @Override
    public String analyzeIntent(String userMessage, String contextInfo) {
        String systemPrompt = buildIntentAnalysisPrompt(contextInfo);

        GptResponse response = chat(systemPrompt, userMessage);

        if (response == null || response.getContent() == null) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "GPT 응답이 비어있습니다");
        }

        return response.getContent();
    }

    /**
     * Intent 분석용 시스템 프롬프트 생성
     */
    private String buildIntentAnalysisPrompt(String contextInfo) {
        return """
                당신은 노인을 위한 음성 인터페이스 AI입니다.
                사용자의 음성 명령을 분석하여 의도(Intent)를 파악하고 필요한 정보를 추출합니다.

                [현재 상황]
                %s

                [가능한 Intent 목록]
                1. SELECT_BY_ARTIST: 가수명으로 노래 검색
                2. SELECT_BY_TITLE: 제목으로 노래 검색
                3. SELECT_BY_ARTIST_TITLE: 가수+제목으로 노래 검색
                4. MUSIC_PAUSE: 일시정지
                5. MUSIC_RESUME: 재생 재개
                6. MUSIC_NEXT: 다음 곡
                7. MUSIC_STOP: 재생 종료
                8. PLAY_NEXT_IN_QUEUE: 대기열 다음 곡 재생
                9. PLAY_MORE_LIKE_THIS: 비슷한 노래 계속 재생
                10. MODE_HOME: 홈으로 이동
                11. MODE_LISTENING: 감상 모드
                12. MODE_EXERCISE: 체조 모드
                13. MODE_EXERCISE_END: 체조 종료
                14. EMERGENCY: 응급 상황
                15. EMERGENCY_CANCEL: 응급 취소
                16. UNKNOWN: 인식 불가

                [응답 형식]
                반드시 JSON 형식으로만 응답하세요:

                예시 1 (노래 검색):
                {
                  "intent": "SELECT_BY_ARTIST",
                  "entities": {
                    "artist": "태진아",
                    "title": "좋은 날"
                  },
                  "confidence": 0.95,
                  "reasoning": "사용자가 태진아의 좋은 날을 요청했습니다"
                }

                예시 2 (응급 상황):
                {
                  "intent": "EMERGENCY",
                  "entities": {
                    "keyword": "살려줘"
                  },
                  "confidence": 0.98,
                  "reasoning": "응급 상황 키워드 감지"
                }

                [중요 규칙]
                - 반드시 JSON만 출력하세요 (다른 설명 금지)
                - intent는 위 목록에서만 선택
                - entities는 필요한 경우만 포함
                - confidence는 0~1 사이 값
                - 한글로 응답
                """.formatted(contextInfo != null ? contextInfo : "컨텍스트 정보 없음");
    }
}
