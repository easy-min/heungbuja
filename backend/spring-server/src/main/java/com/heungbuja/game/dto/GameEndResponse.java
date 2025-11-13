package com.heungbuja.game.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameEndResponse {
    private double finalScore;
    private String message;
}