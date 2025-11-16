-- TTS 캐시 테이블 생성
-- JPA ddl-auto=update로 자동 생성되지만, 참고용으로 작성

CREATE TABLE IF NOT EXISTS tts_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'PK',
    text VARCHAR(500) NOT NULL COMMENT '음성으로 변환할 텍스트',
    voice_type VARCHAR(50) NOT NULL DEFAULT 'default' COMMENT '음성 타입 (nova, alloy, shimmer 등)',
    audio_data MEDIUMBLOB NOT NULL COMMENT 'MP3 바이너리 데이터 (최대 16MB)',
    file_size INT NOT NULL COMMENT '파일 크기 (바이트)',
    hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '캐시 히트 횟수',
    last_used_at TIMESTAMP NULL COMMENT '마지막 사용 시각',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    UNIQUE KEY uk_text_voice_type (text, voice_type),
    INDEX idx_created_at (created_at),
    INDEX idx_voice_type (voice_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TTS 음성 캐시';

-- 캐시 통계 조회 쿼리 예시 (참고용)
-- SELECT
--     COUNT(*) as total_count,
--     SUM(file_size) / 1024 / 1024 as total_size_mb,
--     SUM(hit_count) as total_hits,
--     AVG(file_size / 1024) as avg_size_kb,
--     AVG(hit_count) as avg_hits
-- FROM tts_cache;

-- 히트율 높은 캐시 조회 (참고용)
-- SELECT text, voice_type, hit_count, file_size, created_at
-- FROM tts_cache
-- ORDER BY hit_count DESC
-- LIMIT 10;

-- 오래되고 사용 빈도 낮은 캐시 삭제 (참고용)
-- DELETE FROM tts_cache
-- WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)
--   AND hit_count < 10;
