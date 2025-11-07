package com.heungbuja.admin.entity;

import com.heungbuja.device.entity.Device;
import com.heungbuja.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "facility_name", length = 100)
    private String facilityName;

    @Column(length = 20)
    private String contact;

    @Column(length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AdminRole role = AdminRole.ADMIN;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "admin", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Device> devices = new ArrayList<>();

    @OneToMany(mappedBy = "admin", cascade = CascadeType.ALL)
    @Builder.Default
    private List<User> users = new ArrayList<>();
}
