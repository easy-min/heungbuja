package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GameWebSocketMessage<T> {
    private String type;
    private T data;
}