package com.heungbuja.song.service;

import com.heungbuja.song.entity.Song;

/**
 * 노래 검색 서비스 인터페이스
 */
public interface SongService {

    /**
     * 텍스트로 곡 검색 (가수명 or 곡 제목)
     */
    Song searchSong(String query);

    /**
     * 가수명으로 검색
     */
    Song searchByArtist(String artist);

    /**
     * 곡 제목으로 검색
     */
    Song searchByTitle(String title);

    /**
     * 가수 + 제목으로 검색
     */
    Song searchByArtistAndTitle(String artist, String title);

    /**
     * ID로 노래 조회
     */
    Song findById(Long songId);
}
