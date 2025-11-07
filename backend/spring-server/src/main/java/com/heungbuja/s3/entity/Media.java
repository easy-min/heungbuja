package com.heungbuja.s3.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;             // 제목
    private String type;              // MUSIC / VIDEO
    private String s3Key;             // S3 경로 (예: music/song1.mp3)
    private String bucket;            // 버킷명
    private Long uploaderId;          // 업로더 ID

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
