package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ActionTimelineEvent {
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