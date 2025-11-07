package com.heungbuja.emergency.entity;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "emergency_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmergencyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "trigger_word", nullable = false, length = 100)
    private String triggerWord;

    @Column(name = "full_text", columnDefinition = "TEXT")
    private String fullText;  // 전체 발화 텍스트 (예: "흥부야 신고해줘")

    @Column(name = "is_confirmed", nullable = false)
    @Builder.Default
    private Boolean isConfirmed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private Admin handledBy;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void confirm() {
        this.isConfirmed = true;
        this.status = ReportStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = ReportStatus.FALSE_ALARM;
    }

    public void handle(Admin admin, String notes) {
        this.handledBy = admin;
        this.adminNotes = notes;
        this.status = ReportStatus.RESOLVED;
    }

    public enum ReportStatus {
        PENDING,        // 대기 중 (10초 대기)
        CONFIRMED,      // 확정됨 (관리자 처리 필요)
        RESOLVED,       // 처리 완료
        FALSE_ALARM     // 오탐
    }
}
