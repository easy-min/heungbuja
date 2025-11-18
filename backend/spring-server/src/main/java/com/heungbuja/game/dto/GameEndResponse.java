package com.heungbuja.game.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.Map;

@Getter
@Builder
public class GameEndResponse {
    private double finalScore;
    private String message;
    private Map<String, Double> scoresByAction;
}