package com.heungbuja.voice.service;

import com.heungbuja.voice.dto.VoiceCommandRequest;
import com.heungbuja.voice.dto.VoiceCommandResponse;

/**
 * 음성 명령 처리 서비스 인터페이스
 */
public interface VoiceCommandService {

    /**
     * 음성 명령 처리
     */
    VoiceCommandResponse processCommand(VoiceCommandRequest request);
}
