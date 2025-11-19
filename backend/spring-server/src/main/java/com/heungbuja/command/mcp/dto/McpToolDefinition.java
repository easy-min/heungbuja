package com.heungbuja.command.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool 정의
 * GPT에게 사용 가능한 Tool 정보를 전달하기 위한 메타데이터
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpToolDefinition {

    /**
     * Tool 이름 (GPT가 호출할 때 사용)
     */
    private String name;

    /**
     * Tool 설명 (GPT가 언제 사용할지 판단하는 데 사용)
     */
    private String description;

    /**
     * 파라미터 정의
     * JSON Schema 형식
     */
    private ParameterSchema parameters;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParameterSchema {
        private String type; // "object"
        private Map<String, PropertyDefinition> properties;
        private List<String> required; // 필수 파라미터 목록
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PropertyDefinition {
        private String type; // "string", "number", "integer", "array" 등
        private String description;
        private List<String> enumValues; // enum인 경우 가능한 값들
    }

    /**
     * 헬퍼 메서드: search_song Tool 정의
     */
    public static McpToolDefinition searchSongTool() {
        return McpToolDefinition.builder()
                .name("search_song")
                .description("가수명, 제목, 연대, 장르, 분위기 등으로 노래를 검색합니다. 복잡한 조건도 처리 가능합니다.")
                .parameters(ParameterSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "userId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("사용자 ID")
                                        .build(),
                                "artist", PropertyDefinition.builder()
                                        .type("string")
                                        .description("가수명 (예: 태진아, 조용필)")
                                        .build(),
                                "title", PropertyDefinition.builder()
                                        .type("string")
                                        .description("곡명 (예: 사랑은 아무나 하나)")
                                        .build(),
                                "era", PropertyDefinition.builder()
                                        .type("string")
                                        .description("연대 (예: 1980s, 1990s, 2000s)")
                                        .build(),
                                "genre", PropertyDefinition.builder()
                                        .type("string")
                                        .description("장르 (예: 발라드, 댄스, 트로트)")
                                        .build(),
                                "mood", PropertyDefinition.builder()
                                        .type("string")
                                        .description("분위기 (예: 슬픈, 경쾌한, 잔잔한)")
                                        .build(),
                                "excludeSongId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("제외할 곡 ID (이거 말고 다른 거 요청 시)")
                                        .build()
                        ))
                        .required(List.of("userId"))
                        .build())
                .build();
    }

    /**
     * 헬퍼 메서드: control_playback Tool 정의
     */
    public static McpToolDefinition controlPlaybackTool() {
        return McpToolDefinition.builder()
                .name("control_playback")
                .description("음악 재생을 제어합니다 (일시정지, 재생, 다음곡, 정지)")
                .parameters(ParameterSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "userId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("사용자 ID")
                                        .build(),
                                "action", PropertyDefinition.builder()
                                        .type("string")
                                        .description("재생 제어 동작")
                                        .enumValues(List.of("PAUSE", "RESUME", "NEXT", "STOP"))
                                        .build()
                        ))
                        .required(List.of("userId", "action"))
                        .build())
                .build();
    }

    /**
     * 헬퍼 메서드: add_to_queue Tool 정의
     */
    public static McpToolDefinition addToQueueTool() {
        return McpToolDefinition.builder()
                .name("add_to_queue")
                .description("대기열에 곡을 추가합니다")
                .parameters(ParameterSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "userId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("사용자 ID")
                                        .build(),
                                "artist", PropertyDefinition.builder()
                                        .type("string")
                                        .description("가수명")
                                        .build(),
                                "count", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("추가할 곡 개수 (기본값: 1)")
                                        .build()
                        ))
                        .required(List.of("userId", "artist"))
                        .build())
                .build();
    }

    /**
     * 헬퍼 메서드: get_current_context Tool 정의
     */
    public static McpToolDefinition getCurrentContextTool() {
        return McpToolDefinition.builder()
                .name("get_current_context")
                .description("현재 재생 상태, 대기열 정보를 조회합니다")
                .parameters(ParameterSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "userId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("사용자 ID")
                                        .build()
                        ))
                        .required(List.of("userId"))
                        .build())
                .build();
    }

    /**
     * 헬퍼 메서드: handle_emergency Tool 정의
     */
    public static McpToolDefinition handleEmergencyTool() {
        return McpToolDefinition.builder()
                .name("handle_emergency")
                .description("응급 상황을 감지하고 신고를 생성합니다")
                .parameters(ParameterSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "userId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("사용자 ID")
                                        .build(),
                                "keyword", PropertyDefinition.builder()
                                        .type("string")
                                        .description("응급 키워드 (예: 도와줘, 살려줘)")
                                        .build(),
                                "fullText", PropertyDefinition.builder()
                                        .type("string")
                                        .description("전체 발화 텍스트")
                                        .build()
                        ))
                        .required(List.of("userId", "keyword", "fullText"))
                        .build())
                .build();
    }

    /**
     * 헬퍼 메서드: change_mode Tool 정의
     */
    public static McpToolDefinition changeModeTool() {
        return McpToolDefinition.builder()
                .name("change_mode")
                .description("모드를 변경합니다 (홈, 감상, 체조)")
                .parameters(ParameterSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "userId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("사용자 ID")
                                        .build(),
                                "mode", PropertyDefinition.builder()
                                        .type("string")
                                        .description("모드")
                                        .enumValues(List.of("HOME", "LISTENING", "EXERCISE"))
                                        .build()
                        ))
                        .required(List.of("userId", "mode"))
                        .build())
                .build();
    }

    /**
     * 헬퍼 메서드: start_game Tool 정의
     */
    public static McpToolDefinition startGameTool() {
        return McpToolDefinition.builder()
                .name("start_game")
                .description("게임(체조)을 시작합니다. 노래에 맞춰 동작을 따라하는 게임입니다. 3-5분 소요됩니다.")
                .parameters(ParameterSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "userId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("사용자 ID")
                                        .build(),
                                "songId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("게임에 사용할 노래 ID (선택적, 안무 정보가 있는 노래만 가능)")
                                        .build()
                        ))
                        .required(List.of("userId"))
                        .build())
                .build();
    }

    /**
     * 헬퍼 메서드: start_game_with_song Tool 정의
     * 특정 노래로 게임을 시작 (노래 검색 + 게임 시작을 한 번에 처리)
     */
    public static McpToolDefinition startGameWithSongTool() {
        return McpToolDefinition.builder()
                .name("start_game_with_song")
                .description("특정 노래로 게임(체조)을 시작합니다. 노래 검색과 게임 시작을 한 번에 처리합니다.")
                .parameters(ParameterSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "userId", PropertyDefinition.builder()
                                        .type("integer")
                                        .description("사용자 ID")
                                        .build(),
                                "title", PropertyDefinition.builder()
                                        .type("string")
                                        .description("노래 제목")
                                        .build(),
                                "artist", PropertyDefinition.builder()
                                        .type("string")
                                        .description("가수명 (선택적)")
                                        .build()
                        ))
                        .required(List.of("userId"))
                        .build())
                .build();
    }

    /**
     * 모든 Tool 정의 반환
     */
    public static List<McpToolDefinition> getAllTools() {
        return List.of(
                searchSongTool(),
                controlPlaybackTool(),
                addToQueueTool(),
                getCurrentContextTool(),
                handleEmergencyTool(),
                changeModeTool(),
                startGameTool(),
                startGameWithSongTool()
        );
    }
}
