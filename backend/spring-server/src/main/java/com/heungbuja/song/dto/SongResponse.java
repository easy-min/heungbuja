package com.heungbuja.song.dto;

import com.heungbuja.song.entity.Song;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SongResponse {

    private Long id;
    private String title;
    private String artist;
    private Long mediaId;

    public static SongResponse from(Song song) {
        return SongResponse.builder()
                .id(song.getId())
                .title(song.getTitle())
                .artist(song.getArtist())
                .mediaId(song.getMedia().getId())
                .build();
    }
}
