package com.heungbuja.command.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카메라 세그먼트 정보 DTO (Command 전용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandSegmentInfo {
    private SegmentRange verse1cam;
    private SegmentRange verse2cam;

    /**
     * 각 절(verse)의 카메라 타이밍 범위
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentRange {
        private double startTime;
        private double endTime;
    }
}
