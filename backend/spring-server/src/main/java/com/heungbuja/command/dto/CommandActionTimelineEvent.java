package com.heungbuja.command.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 동작 타임라인 이벤트 DTO (Command 전용)
 * game 도메인의 ActionTimelineEvent를 대체
 */
@Getter
@AllArgsConstructor
public class CommandActionTimelineEvent {
    /**
     * 동작이 시작되는 시간 (초)
     */
    private double time;

    /**
     * 동작의 고유 코드 (actionCode)
     */
    private int actionCode;

    /**
     * 동작의 이름 (예: "손뼉 박수")
     */
    private String actionName;
}
