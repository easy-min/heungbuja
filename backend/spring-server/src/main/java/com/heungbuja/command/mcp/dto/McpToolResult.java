package com.heungbuja.command.mcp.dto;

import com.heungbuja.song.dto.SongInfoDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tool 실행 결과
 * GPT에게 다시 전달됨
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpToolResult {

    /**
     * Tool Call ID (요청과 매칭)
     */
    private String toolCallId;

    /**
     * Tool 이름
     */
    private String toolName;

    /**
     * 성공 여부
     */
    private boolean success;

    /**
     * 결과 메시지
     */
    private String message;

    /**
     * 노래 정보 (search_song의 경우)
     */
    private SongInfoDto songInfo;

    /**
     * 추가 데이터 (JSON 형식)
     */
    private Object data;

    /**
     * 성공 응답 생성 (노래 정보 포함)
     */
    public static McpToolResult success(String toolCallId, String toolName, SongInfoDto songInfo) {
        return McpToolResult.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .success(true)
                .message(String.format("%s의 '%s'를 찾았습니다", songInfo.getArtist(), songInfo.getTitle()))
                .songInfo(songInfo)
                .build();
    }

    /**
     * 성공 응답 생성 (메시지만)
     */
    public static McpToolResult success(String toolCallId, String toolName, String message) {
        return McpToolResult.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .success(true)
                .message(message)
                .build();
    }

    /**
     * 성공 응답 생성 (데이터 포함)
     */
    public static McpToolResult success(String toolCallId, String toolName, String message, Object data) {
        return McpToolResult.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 실패 응답 생성
     */
    public static McpToolResult failure(String toolCallId, String toolName, String errorMessage) {
        return McpToolResult.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .success(false)
                .message(errorMessage)
                .build();
    }
}
