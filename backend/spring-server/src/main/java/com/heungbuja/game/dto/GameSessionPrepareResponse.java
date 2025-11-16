package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 게임 세션 준비 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSessionPrepareResponse {
    private String sessionId;
    private String songTitle;
    private String songArtist;
    private String tutorialVideoUrl;
    private Map<String, String> videoUrls;
}
