package com.heungbuja.gpt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
}
