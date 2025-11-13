package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 노래 섹션 정보 (내부 캐싱용)
 * GameStartResponse는 분리된 구조 사용 (sectionInfo Map + segmentInfo)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionInfo {
    private double introStartTime;
    private double verse1StartTime;
    private double breakStartTime;
    private double verse2StartTime;

    // 카메라 타이밍 정보 (캐싱용)
    private VerseInfo verse1cam;
    private VerseInfo verse2cam;

    /**
     * 각 절(verse)의 카메라 타이밍 정보
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