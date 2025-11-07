package com.heungbuja.voice.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * STT (Speech-to-Text) 서비스 인터페이스
 * 구현체를 교체하여 다양한 STT 엔진 사용 가능 (Whisper, Google STT, Naver Clova 등)
 */
public interface SttService {

    /**
     * 음성 파일을 텍스트로 변환
     *
     * @param audioFile 음성 파일 (WAV, MP3, M4A 등)
     * @return 변환된 텍스트
     */
    String transcribe(MultipartFile audioFile);

    /**
     * 지원하는 오디오 포맷인지 확인
     *
     * @param audioFile 음성 파일
     * @return 지원 여부
     */
    default boolean isSupportedFormat(MultipartFile audioFile) {
        String contentType = audioFile.getContentType();
        return contentType != null && (
                contentType.equals("audio/wav") ||
                contentType.equals("audio/mpeg") ||
                contentType.equals("audio/mp3") ||
                contentType.equals("audio/mp4") ||
                contentType.equals("audio/m4a") ||
                contentType.equals("audio/webm")
        );
    }
}
