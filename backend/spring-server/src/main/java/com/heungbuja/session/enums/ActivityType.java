package com.heungbuja.session.enums;

/**
 * 사용자의 현재 활동 타입
 */
public enum ActivityType {
    /** 유휴 상태 (아무 활동 없음) */
    IDLE,

    /** 게임 진행 중 */
    GAME,

    /** 음악 재생 중 */
    MUSIC,

    /** 응급 상황 */
    EMERGENCY
}
