package com.heungbuja.command.service;

import com.heungbuja.command.dto.IntentResult;

/**
 * 의도 분석기 인터페이스
 *
 * 구현체를 교체하여 다양한 방식의 의도 분석 가능:
 * - 키워드 기반 (KeywordBasedIntentClassifier)
 * - RAG 기반 (RagBasedIntentClassifier)
 * - ML 모델 기반 (MLIntentClassifier)
 * - LLM 기반 (LlmIntentClassifier)
 */
public interface IntentClassifier {

    /**
     * 텍스트 명령어에서 의도(Intent)를 분석
     *
     * @param text 사용자 음성 명령어 텍스트
     * @param userId 사용자 ID (컨텍스트 정보 조회용, null 가능)
     * @return 분석된 의도 및 엔티티 정보
     */
    IntentResult classify(String text, Long userId);

    /**
     * 분석기 타입 반환 (디버깅/로깅용)
     *
     * @return 분석기 타입 (예: "KEYWORD", "RAG", "ML")
     */
    String getClassifierType();
}
