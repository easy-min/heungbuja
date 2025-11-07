package com.heungbuja.command.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통합 명령 요청 DTO
 * - 텍스트 명령어 직접 전달 (디버깅용)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandRequest {

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotNull(message = "명령어 텍스트는 필수입니다")
    private String text;
}
