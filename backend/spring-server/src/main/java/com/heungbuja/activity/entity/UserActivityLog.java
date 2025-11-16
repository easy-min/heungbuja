package com.heungbuja.activity.entity;

import com.heungbuja.activity.enums.ActivityType;
import com.heungbuja.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자 활동 로그 (관리자 페이지 전용)
 * 원본 음성 명령(rawText)을 익명화하여 저장
 */
@Entity
@Table(name = "user_activity_logs", indexes = {
        @Index(name = "idx_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_activity_type", columnList = "activity_type"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 활동 타입 (통계/필터링용)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private ActivityType activityType;

    /**
     * 익명화된 활동 요약
     * 예: "음악을 재생했습니다", "게임을 시작했습니다"
     */
    @Column(name = "activity_summary", nullable = false, length = 100)
    private String activitySummary;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
