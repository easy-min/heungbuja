package com.heungbuja.song.entity;

import com.heungbuja.song.enums.PlaybackMode;
import com.heungbuja.song.entity.Song;
import com.heungbuja.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 노래 청취 이력 (추천 시스템용)
 * 프론트엔드가 음악 재생을 관리하므로, 백엔드는 이력만 기록
 */
@Entity
@Table(name = "listening_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ListeningHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaybackMode mode; // 감상 or 체조

    @CreationTimestamp
    @Column(name = "played_at", updatable = false)
    private LocalDateTime playedAt;
}
