package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LevelDecisionData {
    /** 2절에 진행할 난이도 레벨 (1, 2, 3) */
    private int nextLevel;
    /** 해당 레벨의 시범 캐릭터 영상 Presigned URL */
    private String characterVideoUrl;
}