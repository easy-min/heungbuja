package com.heungbuja.command.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 노래 섹션 정보 DTO (Command 전용)
 * game 도메인의 SectionInfo를 대체
 */
@Getter
@Builder
public class CommandSectionInfo {
    private double introStartTime;
    private double verse1StartTime;
    private double breakStartTime;
    private double verse2StartTime;

    private VerseInfo verse1cam;
    private VerseInfo verse2cam;

    /**
     * 각 절(verse)의 시간 정보를 담는 내부 DTO
     */
    @Getter
    @Builder
    public static class VerseInfo {
        private double startTime;
        private double endTime;
    }
}
