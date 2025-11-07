package com.heungbuja.song.dto;

import com.heungbuja.song.enums.PlaybackMode;
import com.heungbuja.song.entity.Song;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 노래 정보 DTO (프론트 재생용)
 * 프론트가 재생을 관리하므로 상태 정보 없음
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SongInfoDto {

    private Long songId;
    private String title;
    private String artist;
    private Long mediaId;
    private String audioUrl;  // presigned URL (서비스에서 생성)
    private PlaybackMode mode; // 감상 or 체조 모드

    /**
     * Song Entity → DTO 변환 (audioUrl은 서비스에서 설정)
     */
    public static SongInfoDto from(Song song, PlaybackMode mode, String presignedUrl) {
        return SongInfoDto.builder()
                .songId(song.getId())
                .title(song.getTitle())
                .artist(song.getArtist())
                .mediaId(song.getMedia().getId())
                .audioUrl(presignedUrl)
                .mode(mode)
                .build();
    }
}
