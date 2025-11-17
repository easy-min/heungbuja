package com.heungbuja.game.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Motion AI 추론 상세 로그 (MongoDB)
 *
 * 목적: AI 모델의 추론 결과를 저장하여 정확도 분석 및 점수 기준 조정에 활용
 *
 * 활용 방안:
 * 1. targetProbability 분포 분석 → 점수 기준 (60%, 75%, 90%) 적절성 검증
 * 2. 잘못 예측된 케이스 분석 → 모델 재학습 데이터 수집
 * 3. 사용자별 정확도 트렌드 분석
 * 4. 처리 시간 모니터링 (병목 구간 파악)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "motion_inference_logs")
public class MotionInferenceLog {

    @Id
    private String id;

    // 세션 정보
    private String sessionId;
    private Long userId;
    private LocalDateTime timestamp;

    // 목표 동작
    private Integer targetActionCode;
    private String targetActionName;

    // AI 예측 결과
    private Integer predictedActionCode;
    private String predictedActionName;

    // 확률 및 점수
    private Double targetProbability;      // 목표 동작일 확률 (0.0 ~ 1.0)
    private Double maxConfidence;          // 가장 높은 확률 (0.0 ~ 1.0)
    private Integer judgment;              // 최종 점수 (0, 1, 2, 3)

    // 프레임 정보
    private Integer validFrameCount;       // 유효한 프레임 개수
    private Integer totalFrameCount;       // 전송된 총 프레임 개수

    // 처리 시간 (ms)
    private Long totalResponseTimeMs;      // 전체 응답 시간
    private Double decodeTimeMs;           // Base64 디코딩 시간
    private Double poseExtractionTimeMs;   // Mediapipe Pose 추출 시간
    private Double inferenceTimeMs;        // AI 모델 추론 시간

    // 성공 여부
    private Boolean success;               // true: 정상 추론, false: 에러 발생
    private String errorMessage;           // 에러 발생 시 메시지
}
