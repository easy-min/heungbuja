package com.heungbuja.user.dto;

import com.heungbuja.voice.entity.VoiceCommand;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 어르신 최근 활동 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class RecentActivityDto {

    private String intent;        // MUSIC_PAUSE, SELECT_BY_ARTIST 등
    private String description;   // "일시정지", "태진아 노래 검색"
    private String icon;          // "⏸️", "🎵"
    private LocalDateTime timestamp;
    private String timeAgo;       // "5분 전", "1시간 전"

    public static RecentActivityDto from(VoiceCommand command) {
        String intent = command.getIntent();
        String icon = getIconForIntent(intent);
        String description = getDescriptionForIntent(intent, command.getRawText());

        return RecentActivityDto.builder()
                .intent(intent)
                .description(description)
                .icon(icon)
                .timestamp(command.getCreatedAt())
                .timeAgo(calculateTimeAgo(command.getCreatedAt()))
                .build();
    }

    private static String getIconForIntent(String intent) {
        if (intent == null) return "🎤";

        return switch (intent) {
            case "SELECT_BY_ARTIST", "SELECT_BY_TITLE", "SELECT_BY_ARTIST_TITLE" -> "🎵";
            case "MUSIC_PAUSE" -> "⏸️";
            case "MUSIC_RESUME" -> "▶️";
            case "MUSIC_NEXT" -> "⏭️";
            case "MUSIC_STOP" -> "⏹️";
            case "MODE_LISTENING_START" -> "🎧";
            case "MODE_EXERCISE_START" -> "🧘";
            case "EMERGENCY" -> "🚨";
            case "EMERGENCY_CANCEL" -> "✅";
            default -> "🎤";
        };
    }

    private static String getDescriptionForIntent(String intent, String rawText) {
        if (intent == null) return "음성 명령";

        return switch (intent) {
            case "SELECT_BY_ARTIST" -> "가수 검색: " + extractQuery(rawText);
            case "SELECT_BY_TITLE" -> "노래 검색: " + extractQuery(rawText);
            case "SELECT_BY_ARTIST_TITLE" -> "노래 재생: " + extractQuery(rawText);
            case "MUSIC_PAUSE" -> "일시정지";
            case "MUSIC_RESUME" -> "재생 재개";
            case "MUSIC_NEXT" -> "다음 곡";
            case "MUSIC_STOP" -> "재생 종료";
            case "MODE_LISTENING_START" -> "감상 모드 시작";
            case "MODE_EXERCISE_START" -> "체조 모드 시작";
            case "MODE_SWITCH_TO_LISTENING" -> "감상 모드로 전환";
            case "MODE_SWITCH_TO_EXERCISE" -> "체조 모드로 전환";
            case "EMERGENCY" -> "긴급 신고";
            case "EMERGENCY_CANCEL" -> "신고 취소";
            default -> rawText;
        };
    }

    private static String extractQuery(String rawText) {
        if (rawText == null) return "";
        // "틀어줘", "들려줘" 등 제거
        return rawText.replaceAll("(틀어줘|틀어|들려줘|들려|재생|해줘|해)", "").trim();
    }

    private static String calculateTimeAgo(LocalDateTime timestamp) {
        if (timestamp == null) return "";

        LocalDateTime now = LocalDateTime.now();
        long minutes = java.time.Duration.between(timestamp, now).toMinutes();

        if (minutes < 1) return "방금 전";
        if (minutes < 60) return minutes + "분 전";

        long hours = minutes / 60;
        if (hours < 24) return hours + "시간 전";

        long days = hours / 24;
        return days + "일 전";
    }
}
