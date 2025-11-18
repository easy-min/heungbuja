package com.heungbuja.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Motion AI 서버 응답 DTO
 *
 * 업데이트: 정확도 분석을 위한 확률값 추가
 * - predictedLabel: AI가 예측한 동작 이름
 * - confidence: 예측 신뢰도 (0.0 ~ 1.0)
 * - targetProbability: 목표 동작일 확률 (0.0 ~ 1.0)
 */
@Getter
@NoArgsConstructor // JSON 역직렬화를 위해 기본 생성자 추가
@AllArgsConstructor
public class AiJudgmentResponse {
    private int actionCode;
    private int judgment;

    // AI 모델 추론 상세 정보 (MongoDB 저장용)
    private String predictedLabel;
    private double confidence;
    private Double targetProbability;  // null 가능

    // 처리 시간 분석
    private double decodeTimeMs;
    private double poseTimeMs;
    private double inferenceTimeMs;
}