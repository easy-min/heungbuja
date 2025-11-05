package com.heungbuja.voice.dto;

import com.heungbuja.song.dto.SongResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VoiceCommandResponse {

    private Long commandId;
    private String intent;
    private SongResponse song;  // PLAY_SONG일 때만
    private String message;     // TTS 메시지

    public static VoiceCommandResponse playSong(Long commandId, SongResponse song, String message) {
        return VoiceCommandResponse.builder()
                .commandId(commandId)
                .intent("PLAY_SONG")
                .song(song)
                .message(message)
                .build();
    }

    public static VoiceCommandResponse simpleIntent(Long commandId, String intent) {
        return VoiceCommandResponse.builder()
                .commandId(commandId)
                .intent(intent)
                .build();
    }

    public static VoiceCommandResponse withMessage(Long commandId, String intent, String message) {
        return VoiceCommandResponse.builder()
                .commandId(commandId)
                .intent(intent)
                .message(message)
                .build();
    }
}
