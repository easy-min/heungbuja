package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStartRequest {
    // 이 userId는 이제 User 테이블의 PK(Id)를 의미
    private Long userId;
    private Long songId;
}