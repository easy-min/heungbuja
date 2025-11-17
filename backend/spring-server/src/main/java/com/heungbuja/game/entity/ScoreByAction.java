package com.heungbuja.game.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreByAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_result_id", nullable = false)
    private GameResult gameResult;

    @Column(nullable = false)
    private Integer actionCode;

    @Column(nullable = false)
    private Double averageScore;

    @Builder
    public ScoreByAction(GameResult gameResult, Integer actionCode, Double averageScore) {
        this.gameResult = gameResult;
        this.actionCode = actionCode;
        this.averageScore = averageScore;
    }
}