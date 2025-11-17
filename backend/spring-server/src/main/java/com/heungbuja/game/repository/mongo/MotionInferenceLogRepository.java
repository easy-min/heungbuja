package com.heungbuja.game.repository.mongo;

import com.heungbuja.game.domain.MotionInferenceLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MotionInferenceLogRepository extends MongoRepository<MotionInferenceLog, String> {

    /**
     * 세션별 추론 로그 조회
     */
    List<MotionInferenceLog> findBySessionIdOrderByTimestampAsc(String sessionId);

    /**
     * 사용자별 추론 로그 조회 (최근순)
     */
    List<MotionInferenceLog> findByUserIdOrderByTimestampDesc(Long userId);

    /**
     * 기간별 추론 로그 조회 (정확도 분석용)
     */
    List<MotionInferenceLog> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 특정 동작의 추론 로그 조회 (모델 성능 분석용)
     */
    List<MotionInferenceLog> findByTargetActionCode(Integer actionCode);
}
