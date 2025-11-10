package com.heungbuja.song.enums;

/**
 * 재생 상태
 */
public enum PlaybackStatus {
    PLAYING("재생 중"),
    PAUSED("일시정지"),
    STOPPED("정지");

    private final String description;

    PlaybackStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
