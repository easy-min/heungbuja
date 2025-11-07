package com.heungbuja.voice.service.impl;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.voice.service.SttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Mock STT 서비스 (개발/테스트용)
 * 실제 Whisper API 연동 전까지 사용
 */
@Slf4j
@Service
@Profile({"dev", "local", "default"})
public class MockSttServiceImpl implements SttService {

    @Override
    public String transcribe(MultipartFile audioFile) {
        if (!isSupportedFormat(audioFile)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "지원하지 않는 오디오 포맷입니다");
        }

        log.info("Mock STT 처리: 파일명={}, 크기={} bytes",
                audioFile.getOriginalFilename(), audioFile.getSize());

        // TODO: 실제 Whisper API 호출로 대체
        // Mock 응답 (테스트용)
        return "태진아 노래 틀어줘";
    }
}
