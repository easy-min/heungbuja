package com.heungbuja.gpt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GPT API 요청 DTO (GMS API 형식)
 * model과 messages만 전송
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GptRequest {

    @Builder.Default
    private String model = "gpt-5-nano";

    private List<GptMessage> messages;

    // 성능 최적화 파라미터
    @JsonProperty("max_tokens")
    @Builder.Default
    private Integer maxTokens = 200;  // JSON 응답용으로 200 토큰 제한

    @Builder.Default
    private Double temperature = 0.3;  // 낮은 temperature로 빠른 응답
}
