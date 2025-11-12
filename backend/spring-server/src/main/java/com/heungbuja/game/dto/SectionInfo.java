package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionInfo {
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerseInfo {
        private double startTime;
        private double endTime;
    }
}