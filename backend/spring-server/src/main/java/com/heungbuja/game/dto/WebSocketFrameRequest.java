package com.heungbuja.game.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WebSocketFrameRequest {
    /**
     * 현재 게임 세션을 식별하는 고유 ID
     */
    private String sessionId;

    /**
     * Base64로 인코딩된 단일 프레임 이미지 데이터
     */
    private String frameData;

    /**
     * 현재 노래 재생 시간 (초 단위, 예: 35.78)
     */
    private double currentPlayTime;
}