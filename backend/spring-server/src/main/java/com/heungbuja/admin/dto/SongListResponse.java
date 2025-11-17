package com.heungbuja.admin.dto;

import com.heungbuja.song.entity.Song;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 곡 목록 응답 DTO
 * - 관리자 페이지에서 곡 선택용
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SongListResponse {

    /**
     * 곡 ID
     */
    private Long id;

    /**
     * 곡 제목
     */
    private String title;

    /**
     * 아티스트
     */
    private String artist;

    /**
     * Song 엔티티로부터 변환
     */
    public static SongListResponse from(Song song) {
        return SongListResponse.builder()
                .id(song.getId())
                .title(song.getTitle())
                .artist(song.getArtist())
                .build();
    }
}
