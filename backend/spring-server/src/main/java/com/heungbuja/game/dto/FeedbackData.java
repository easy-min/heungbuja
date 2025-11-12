package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackData {
    /** 판정 결과 (1: SOSO, 2: GOOD, 3: PERFECT) */
    private int judgment;
    /** 판정이 내려진 시점의 노래 재생 시간 */
    private double timestamp;
}