package com.heungbuja.song.enums;

/**
 * 노래 검색 타입
 */
public enum SearchType {
    BY_ARTIST("가수명으로 검색"),
    BY_TITLE("제목으로 검색"),
    BY_ARTIST_AND_TITLE("가수+제목으로 검색"),
    BY_QUERY("통합 검색");

    private final String description;

    SearchType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
