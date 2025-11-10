package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GameWebSocketMessage<T> {
    private String type;
    private T data;
}