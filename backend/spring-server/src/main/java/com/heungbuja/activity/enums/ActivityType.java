package com.heungbuja.activity.enums;

/**
 * 사용자 활동 타입
 * 관리자 페이지에서 통계/필터링용
 */
public enum ActivityType {
    MUSIC_PLAY("음악 재생"),
    MUSIC_CONTROL("음악 제어"),
    GAME_START("게임 시작"),
    GAME_END("게임 종료"),
    MODE_CHANGE("모드 변경"),
    EMERGENCY("응급 상황"),
    SEARCH("노래 검색"),
    UNKNOWN("인식 불가");

    private final String description;

    ActivityType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
