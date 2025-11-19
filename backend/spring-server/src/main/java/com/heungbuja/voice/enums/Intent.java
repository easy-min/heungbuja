package com.heungbuja.voice.enums;

/**
 * 음성 명령 의도 (Intent)
 */
public enum Intent {
    // 음악 재생 관련
    SELECT_BY_ARTIST("가수명으로 노래 검색"),
    SELECT_BY_TITLE("제목으로 노래 검색"),
    SELECT_BY_ARTIST_TITLE("가수+제목으로 노래 검색"),

    // 재생 제어
    MUSIC_PAUSE("일시정지"),
    MUSIC_RESUME("재생 재개"),
    MUSIC_NEXT("다음 곡"),
    MUSIC_STOP("재생 종료"),

    // 연속 재생
    PLAY_NEXT_IN_QUEUE("대기열 다음 곡 재생"),
    PLAY_MORE_LIKE_THIS("비슷한 노래 계속 재생"),

    // 모드 관련 (단순화)
    MODE_HOME("홈으로 이동"),
    MODE_LISTENING("감상 모드"),
    MODE_EXERCISE("체조 모드"),
    MODE_EXERCISE_END("체조 종료"),

    // 응급 상황
    EMERGENCY("응급 상황 감지"),
    EMERGENCY_CANCEL("응급 상황 취소"),
    EMERGENCY_CONFIRM("응급 상황 즉시 확정"),

    // 기타
    UNKNOWN("인식 불가");

    private final String description;

    Intent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
