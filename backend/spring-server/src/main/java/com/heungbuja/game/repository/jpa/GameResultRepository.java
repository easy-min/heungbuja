package com.heungbuja.game.repository.jpa;

import com.heungbuja.game.entity.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    // (예시) 나중에 특정 유저의 모든 게임 기록을 조회할 때 사용할 수 있는 쿼리 메소드
    List<GameResult> findByUser_IdOrderByStartTimeDesc(Long userId);

    // 세션 ID로 게임 결과 조회
    Optional<GameResult> findBySessionId(String sessionId);

    // --- Fetch Join을 사용하여 GameResult와 ScoreByAction을 함께 조회 ---
    @Query("SELECT gr FROM GameResult gr LEFT JOIN FETCH gr.scoresByAction WHERE gr.sessionId = :sessionId")
    Optional<GameResult> findBySessionIdWithScores(@Param("sessionId") String sessionId);

}