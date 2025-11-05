package com.heungbuja.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
// @JsonInclude(JsonInclude.Include.NON_NULL) // nextLevel이 null일 경우 JSON에서 제외하는 옵션
public class FrameBatchResponse {
    /** 현재 상태 ("PROCESSING": 처리 중, "LEVEL_DECIDED": 1절 종료 및 레벨 결정 완료) */
    private String status;

    /** 1절 종료 시에만 값이 채워짐 (2절에 보여줄 안무 레벨: 1, 2, 3) */
    private Integer nextLevel;
}