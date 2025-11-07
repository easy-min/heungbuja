package com.heungbuja.user.entity;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.auth.entity.RefreshToken;
import com.heungbuja.device.entity.Device;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "medical_notes", columnDefinition = "TEXT")
    private String medicalNotes;

    @Column(name = "emergency_contact", length = 20)
    private String emergencyContact;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", unique = true)
    private Device device;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void assignDevice(Device device) {
        this.device = device;
        if (device != null) {
            device.updateStatus(Device.DeviceStatus.ACTIVE);
        }
    }

    public void deactivate() {
        this.isActive = false;
        if (this.device != null) {
            this.device.updateStatus(Device.DeviceStatus.REGISTERED);
            this.device = null;
        }
        // Refresh tokens will be deleted via orphanRemoval
        this.refreshTokens.clear();
    }

    public void updateInfo(String name, LocalDate birthDate, Gender gender,
                          String medicalNotes, String emergencyContact) {
        if (name != null) this.name = name;
        if (birthDate != null) this.birthDate = birthDate;
        if (gender != null) this.gender = gender;
        if (medicalNotes != null) this.medicalNotes = medicalNotes;
        if (emergencyContact != null) this.emergencyContact = emergencyContact;
    }

    public enum Gender {
        MALE, FEMALE
    }
}
