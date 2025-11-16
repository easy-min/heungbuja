package com.heungbuja.voice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * TTS 음성 캐시 엔티티
 * 동일한 텍스트와 음성 타입에 대해 TTS API를 반복 호출하지 않고
 * DB에 캐싱된 음성 데이터를 재사용하여 성능과 비용을 최적화
 */
@Entity
@Table(
    name = "tts_cache",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_text_voice_type",
        columnNames = {"text", "voice_type"}
    ),
    indexes = {
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_voice_type", columnList = "voice_type")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TtsCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 음성으로 변환할 텍스트
     * 예: "일시정지할게요", "태진아의 '좋은 날'을 재생할게요"
     */
    @Column(nullable = false, length = 500)
    private String text;

    /**
     * 음성 타입
     * nova(default), alloy(urgent), shimmer(calm), echo(energetic), onyx(male) 등
     */
    @Column(name = "voice_type", nullable = false, length = 50)
    @Builder.Default
    private String voiceType = "default";

    /**
     * MP3 바이너리 데이터
     * MEDIUMBLOB: 최대 16MB (일반적인 TTS 음성은 10-100KB)
     */
    @Lob
    @Column(name = "audio_data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] audioData;

    /**
     * 파일 크기 (바이트)
     */
    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    /**
     * 캐시 히트 횟수 (성능 모니터링용)
     */
    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private Long hitCount = 0L;

    /**
     * 마지막 사용 시각 (캐시 정리 정책용)
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * 생성 시각
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 시각
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 캐시 히트 시 호출 (히트 횟수 증가, 마지막 사용 시각 갱신)
     */
    public void recordHit() {
        this.hitCount++;
        this.lastUsedAt = LocalDateTime.now();
    }
}
