package com.heungbuja.command.dto;

import com.heungbuja.song.dto.SongInfoDto;
import com.heungbuja.voice.enums.Intent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통합 명령 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandResponse {

    private boolean success;

    private Intent intent;

    private String responseText; // TTS로 변환될 응답 메시지

    private String ttsAudioUrl; // TTS 음성 파일 URL

    private SongInfoDto songInfo; // 노래 정보 (노래 재생 시)

    /**
     * 성공 응답 생성
     */
    public static CommandResponse success(Intent intent, String responseText, String ttsAudioUrl) {
        return CommandResponse.builder()
                .success(true)
                .intent(intent)
                .responseText(responseText)
                .ttsAudioUrl(ttsAudioUrl)
                .build();
    }

    /**
     * 노래 정보와 함께 응답 생성
     */
    public static CommandResponse withSong(Intent intent, String responseText,
                                           String ttsAudioUrl, SongInfoDto songInfo) {
        return CommandResponse.builder()
                .success(true)
                .intent(intent)
                .responseText(responseText)
                .ttsAudioUrl(ttsAudioUrl)
                .songInfo(songInfo)
                .build();
    }

    /**
     * 실패 응답 생성
     */
    public static CommandResponse failure(Intent intent, String responseText, String ttsAudioUrl) {
        return CommandResponse.builder()
                .success(false)
                .intent(intent)
                .responseText(responseText)
                .ttsAudioUrl(ttsAudioUrl)
                .build();
    }
}
