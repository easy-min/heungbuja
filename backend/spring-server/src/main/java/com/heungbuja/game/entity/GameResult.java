package com.heungbuja.game.entity;

import com.heungbuja.song.entity.Song;
import com.heungbuja.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "game_result")
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // User(1) <-> GameResult(N)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY) // Song(1) <-> GameResult(N)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(name = "verse1_avg_score")
    private Double verse1AvgScore;

    @Column(name = "verse2_avg_score")
    private Double verse2AvgScore;

    private Integer finalLevel;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime playedAt;

    @Builder
    public GameResult(User user, Song song, Double verse1AvgScore, Double verse2AvgScore, Integer finalLevel) {
        this.user = user;
        this.song = song;
        this.verse1AvgScore = verse1AvgScore;
        this.verse2AvgScore = verse2AvgScore;
        this.finalLevel = finalLevel;
    }
}