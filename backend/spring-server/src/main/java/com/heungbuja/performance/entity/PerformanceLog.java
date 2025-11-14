package com.heungbuja.performance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 성능 측정 로그 엔티티
 */
@Entity
@Table(name = "performance_logs", indexes = {
    @Index(name = "idx_component", columnList = "component"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PerformanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 측정 대상 컴포넌트 (예: "STT", "GPT", "SongSearch", "TTS")
     */
    @Column(nullable = false, length = 100)
    private String component;

    /**
     * 메서드명 (예: "processTextCommand", "searchByTitle")
     */
    @Column(nullable = false, length = 200)
    private String methodName;

    /**
     * 실행 시간 (밀리초)
     */
    @Column(nullable = false)
    private Long executionTimeMs;

    /**
     * 요청 ID (같은 요청 내 여러 측정을 묶기 위함)
     */
    @Column(length = 100)
    private String requestId;

    /**
     * 사용자 ID (선택적)
     */
    private Long userId;

    /**
     * 성공 여부
     */
    @Column(nullable = false)
    private Boolean success;

    /**
     * 에러 메시지 (실패 시)
     */
    @Column(length = 500)
    private String errorMessage;

    /**
     * 추가 정보 (JSON 형태)
     */
    @Column(length = 1000)
    private String metadata;

    /**
     * 생성 시간
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
