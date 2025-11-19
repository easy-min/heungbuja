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
}
