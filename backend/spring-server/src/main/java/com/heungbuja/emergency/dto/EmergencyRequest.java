package com.heungbuja.emergency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Trigger word is required")
    private String triggerWord;

    // 전체 발화 텍스트 (예: "흥부야 신고해줘")
    private String fullText;
}
