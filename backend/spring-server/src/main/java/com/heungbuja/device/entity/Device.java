package com.heungbuja.device.entity;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @Column(length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.REGISTERED;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @OneToOne(mappedBy = "device")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateStatus(DeviceStatus status) {
        this.status = status;
    }

    public void updateLastActiveAt() {
        this.lastActiveAt = LocalDateTime.now();
    }

    public void updateLocation(String location) {
        this.location = location;
    }

    public enum DeviceStatus {
        REGISTERED,  // 등록됨, 어르신 미매칭
        ACTIVE,      // 어르신과 매칭되어 사용 중
        MAINTENANCE, // 수리 중
        INACTIVE     // 사용 중지
    }
}
