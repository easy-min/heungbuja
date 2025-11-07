package com.heungbuja.voice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VoiceCommandRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Text is required")
    private String text;
}
