package com.heungbuja.voice.repository;

import com.heungbuja.voice.entity.TtsCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TtsCacheRepository extends JpaRepository<TtsCache, Long> {

    /**
     * 텍스트와 음성 타입으로 캐시 조회
     * 가장 자주 사용되는 메서드 (캐시 히트/미스 판단)
     */
    Optional<TtsCache> findByTextAndVoiceType(String text, String voiceType);

    /**
     * 텍스트와 음성 타입으로 캐시 존재 여부 확인
     */
    boolean existsByTextAndVoiceType(String text, String voiceType);

    /**
     * 특정 음성 타입의 모든 캐시 조회
     */
    long countByVoiceType(String voiceType);

    /**
     * 오래되고 사용 빈도가 낮은 캐시 삭제 (정리 정책)
     * @param threshold 기준 날짜 (예: 6개월 전)
     * @param minHitCount 최소 히트 횟수
     * @return 삭제된 개수
     */
    @Modifying
    @Query("DELETE FROM TtsCache t WHERE t.createdAt < :threshold AND t.hitCount < :minHitCount")
    int deleteOldUnusedCache(@Param("threshold") LocalDateTime threshold,
                             @Param("minHitCount") long minHitCount);

    /**
     * 캐시 통계: 총 저장 용량 (바이트)
     */
    @Query("SELECT COALESCE(SUM(t.fileSize), 0) FROM TtsCache t")
    long getTotalStorageSize();

    /**
     * 캐시 통계: 총 히트 횟수
     */
    @Query("SELECT COALESCE(SUM(t.hitCount), 0) FROM TtsCache t")
    long getTotalHitCount();
}
