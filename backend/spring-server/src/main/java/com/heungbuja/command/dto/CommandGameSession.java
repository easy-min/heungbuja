package com.heungbuja.command.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 세션 준비 응답 DTO (Command 전용)
 * game 도메인의 GameSessionPrepareResponse를 대체
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandGameSession {
    private String sessionId;
    private String songTitle;
    private String songArtist;
    private String tutorialVideoUrl;
}
