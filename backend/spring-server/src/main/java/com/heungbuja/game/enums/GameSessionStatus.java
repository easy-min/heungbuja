package com.heungbuja.game.enums;

/**
 * 게임 세션 상태
 */
public enum GameSessionStatus {
    /** 게임 시작됨 */
    STARTED,

    /** 게임 진행 중 */
    IN_PROGRESS,

    /** 게임 정상 완료 */
    COMPLETED,

    /** 게임 중단됨 (인터럽트) */
    INTERRUPTED
}
