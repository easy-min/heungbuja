package com.heungbuja.game.entity;

import com.heungbuja.game.enums.GameSessionStatus;
import com.heungbuja.song.entity.Song;
import com.heungbuja.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
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

    /** 게임 세션 ID (UUID) */
    @Column(name = "session_id", unique = true)
    private String sessionId;

    /** 게임 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private GameSessionStatus status;

    /** 게임 시작 시간 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 게임 종료 시간 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 인터럽트 사유 */
    @Column(name = "interrupt_reason")
    private String interruptReason;

    @Column(name = "verse1_avg_score")
    private Double verse1AvgScore;

    @Column(name = "verse2_avg_score")
    private Double verse2AvgScore;

    @Column(name = "final_level")
    private Integer finalLevel;

    @CreatedDate  // 엔티티가 처음 저장될 때 시간이 자동으로 저장됨
    @Column(updatable = false)  // 이 값은 업데이트되지 않도록 설정
    private LocalDateTime playedAt; // ERD의 played_at. createdAt의 역할을 함.

    @LastModifiedDate // 엔티티가 수정될 때마다 시간이 자동으로 갱신됨
    private LocalDateTime updatedAt; // updatedAt 추가

    @Builder
    public GameResult(User user, Song song, String sessionId, GameSessionStatus status,
                     LocalDateTime startTime, LocalDateTime endTime, String interruptReason,
                     Double verse1AvgScore, Double verse2AvgScore, Integer finalLevel) {
        this.user = user;
        this.song = song;
        this.sessionId = sessionId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.interruptReason = interruptReason;
        this.verse1AvgScore = verse1AvgScore;
        this.verse2AvgScore = verse2AvgScore;
        this.finalLevel = finalLevel;
    }

    /**
     * 게임 중단 처리
     */
    public void interrupt(String reason) {
        this.status = GameSessionStatus.INTERRUPTED;
        this.endTime = LocalDateTime.now();
        this.interruptReason = reason;
    }

    /**
     * 게임 완료 처리
     */
    public void complete() {
        this.status = GameSessionStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
    }
}