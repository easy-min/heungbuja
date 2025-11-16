package com.heungbuja.voice.service;

import com.heungbuja.voice.entity.TtsCache;
import com.heungbuja.voice.repository.TtsCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * TTS 캐시 서비스
 * TTS 음성 데이터를 DB에 캐싱하여 동일한 텍스트에 대해
 * OpenAI TTS API를 반복 호출하지 않고 재사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TtsCacheService {

    private final TtsCacheRepository ttsCacheRepository;

    /**
     * 캐시된 TTS 음성 조회 또는 생성
     *
     * @param text 음성으로 변환할 텍스트
     * @param voiceType 음성 타입 (nova, alloy, shimmer 등)
     * @param generator TTS 생성 함수 (캐시 미스 시 호출)
     * @return MP3 바이너리 데이터
     */
    @Transactional
    public byte[] getCachedOrGenerate(String text, String voiceType, Supplier<byte[]> generator) {
        // 1. 캐시 조회
        Optional<TtsCache> cached = ttsCacheRepository.findByTextAndVoiceType(text, voiceType);

        if (cached.isPresent()) {
            // 캐시 히트
            TtsCache ttsCache = cached.get();
            ttsCache.recordHit();
            ttsCacheRepository.save(ttsCache);

            log.info("TTS Cache HIT - text: '{}', voiceType: {}, hitCount: {}",
                text, voiceType, ttsCache.getHitCount());

            return ttsCache.getAudioData();
        }

        // 2. 캐시 미스 - TTS 생성
        log.info("TTS Cache MISS - text: '{}', voiceType: {}", text, voiceType);
        byte[] audioData = generator.get();

        // 3. 캐시 저장
        TtsCache newCache = TtsCache.builder()
            .text(text)
            .voiceType(voiceType)
            .audioData(audioData)
            .fileSize(audioData.length)
            .hitCount(0L)
            .lastUsedAt(LocalDateTime.now())
            .build();

        ttsCacheRepository.save(newCache);

        log.info("TTS Cache SAVED - text: '{}', voiceType: {}, size: {} bytes",
            text, voiceType, audioData.length);

        return audioData;
    }

    /**
     * 캐시 존재 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean exists(String text, String voiceType) {
        return ttsCacheRepository.existsByTextAndVoiceType(text, voiceType);
    }

    /**
     * 오래되고 사용 빈도가 낮은 캐시 삭제
     *
     * @param beforeDate 기준 날짜 (이 날짜 이전 생성된 캐시)
     * @param minHitCount 최소 히트 횟수 (이 값 미만인 캐시)
     * @return 삭제된 캐시 개수
     */
    @Transactional
    public int cleanupOldCache(LocalDateTime beforeDate, long minHitCount) {
        int deletedCount = ttsCacheRepository.deleteOldUnusedCache(beforeDate, minHitCount);
        log.info("TTS Cache CLEANUP - deleted: {} entries (before: {}, minHitCount: {})",
            deletedCount, beforeDate, minHitCount);
        return deletedCount;
    }

    /**
     * 캐시 통계 조회
     */
    @Transactional(readOnly = true)
    public CacheStats getCacheStats() {
        long totalCount = ttsCacheRepository.count();
        long totalSize = ttsCacheRepository.getTotalStorageSize();
        long totalHits = ttsCacheRepository.getTotalHitCount();

        return new CacheStats(totalCount, totalSize, totalHits);
    }

    /**
     * 캐시 통계 DTO
     */
    public record CacheStats(
        long totalCount,      // 총 캐시 개수
        long totalSize,       // 총 저장 용량 (바이트)
        long totalHits        // 총 히트 횟수
    ) {
        public double getAverageSizeKb() {
            return totalCount > 0 ? (totalSize / 1024.0 / totalCount) : 0;
        }

        public double getAverageHits() {
            return totalCount > 0 ? ((double) totalHits / totalCount) : 0;
        }

        public String getTotalSizeMb() {
            return String.format("%.2f MB", totalSize / 1024.0 / 1024.0);
        }
    }
}
