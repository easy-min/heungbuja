package com.heungbuja.gpt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * GPT API 메시지 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GptMessage {

    private String role;      // "system", "user", "assistant", "developer"
    private String content;   // 메시지 내용

    public static GptMessage system(String content) {
        return GptMessage.builder()
                .role("system")
                .content(content)
                .build();
    }

    public static GptMessage developer(String content) {
        return GptMessage.builder()
                .role("developer")
                .content(content)
                .build();
    }

    public static GptMessage user(String content) {
        return GptMessage.builder()
                .role("user")
                .content(content)
                .build();
    }
}
