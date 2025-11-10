package com.heungbuja.voice.service;

/**
 * TTS (Text-to-Speech) 서비스 인터페이스
 * 구현체를 교체하여 다양한 TTS 엔진 사용 가능 (Google TTS, AWS Polly, Naver Clova 등)
 */
public interface TtsService {

    /**
     * 텍스트를 음성으로 변환하고 저장
     *
     * @param text 변환할 텍스트
     * @param voiceType 음성 타입 (예: 여성, 남성, 노인 등)
     * @return 생성된 음성 파일 경로 또는 URL
     */
    String synthesize(String text, String voiceType);

    /**
     * 텍스트를 음성으로 변환 (기본 음성)
     *
     * @param text 변환할 텍스트
     * @return 생성된 음성 파일 경로 또는 URL
     */
    default String synthesize(String text) {
        return synthesize(text, "default");
    }

    /**
     * 저장된 TTS 음성 파일 가져오기
     *
     * @param fileId 파일 ID
     * @return 음성 파일 바이트 배열
     */
    byte[] getAudioFile(String fileId);

    /**
     * 텍스트를 음성으로 변환하여 바이트 배열로 직접 반환 (파일 저장 없음)
     *
     * @param text 변환할 텍스트
     * @param voiceType 음성 타입
     * @return 음성 데이터 바이트 배열 (MP3)
     */
    byte[] synthesizeBytes(String text, String voiceType);
}
