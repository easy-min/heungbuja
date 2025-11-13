package com.heungbuja.game.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class AiAnalyzeRequest {
    private int actionCode;
    private String actionName;
    private int frameCount;
    private List<String> frames;
}