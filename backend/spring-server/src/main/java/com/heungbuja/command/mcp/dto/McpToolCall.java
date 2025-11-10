package com.heungbuja.command.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * GPT가 요청한 Tool 호출 정보
 * GPT 응답에서 파싱됨
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpToolCall {

    /**
     * Tool 이름
     */
    private String name;

    /**
     * Tool 파라미터
     * JSON 객체를 Map으로 파싱
     */
    private Map<String, Object> arguments;

    /**
     * Tool Call ID (GPT가 생성, 응답 매칭용)
     */
    private String id;
}
