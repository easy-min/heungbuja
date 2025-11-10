package com.heungbuja.game.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class SectionInfo {
    private double introStartTime;
    private double verse1StartTime;
    private double breakStartTime;
    private double verse2StartTime;

    /** 1절의 각 16비트 묶음(세그먼트)별 시작 시간 리스트 */
    private List<Double> verse1SegmentStartTimes;

    /** 2절의 각 16비트 묶음(세그먼트)별 시작 시간 리스트 */
    private List<Double> verse2SegmentStartTimes;
}