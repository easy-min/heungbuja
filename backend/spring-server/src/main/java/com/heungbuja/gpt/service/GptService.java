package com.heungbuja.gpt.service;

import com.heungbuja.gpt.dto.GptMessage;
import com.heungbuja.gpt.dto.GptResponse;

import java.util.List;

/**
 * GPT API 서비스 인터페이스
 */
public interface GptService {

    /**
     * GPT API 호출 (메시지 리스트)
     */
    GptResponse chat(List<GptMessage> messages);

    /**
     * GPT API 호출 (단일 사용자 메시지)
     */
    GptResponse chat(String userMessage);

    /**
     * GPT API 호출 (시스템 프롬프트 + 사용자 메시지)
     */
    GptResponse chat(String systemPrompt, String userMessage);

    /**
     * Intent 분석용 GPT 호출
     *
     * @param userMessage 사용자 음성 명령어
     * @param contextInfo 대화 컨텍스트 정보 (현재 모드, 재생 중인 곡 등)
     * @return JSON 형식의 Intent 분석 결과
     */
    String analyzeIntent(String userMessage, String contextInfo);

    /**
     * Intent 분석용 GPT 호출 (최적화 버전)
     * - 짧은 프롬프트 사용 (8줄 vs 원본 55줄)
     * - 15초 타임아웃 적용
     * - HTTP/2 사용
     *
     * @param userMessage 사용자 음성 명령어
     * @param contextInfo 대화 컨텍스트 정보 (사용 안 함)
     * @return JSON 형식의 Intent 분석 결과
     */
    String analyzeIntentOptimized(String userMessage, String contextInfo);
}
