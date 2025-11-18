package com.heungbuja.game.repository.jpa;

import com.heungbuja.game.entity.GameResult;
import com.heungbuja.game.enums.GameSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    // (예시) 나중에 특정 유저의 모든 게임 기록을 조회할 때 사용할 수 있는 쿼리 메소드
    List<GameResult> findByUser_IdOrderByStartTimeDesc(Long userId);

    // 세션 ID로 게임 결과 조회
    Optional<GameResult> findBySessionId(String sessionId);

    // --- Fetch Join을 사용하여 GameResult와 ScoreByAction을 함께 조회 ---
    @Query("SELECT gr FROM GameResult gr LEFT JOIN FETCH gr.scoresByAction WHERE gr.sessionId = :sessionId")
    Optional<GameResult> findBySessionIdWithScores(@Param("sessionId") String sessionId);

    // --- 관리자 건강 모니터링용 쿼리 메소드 ---

    // 사용자별 총 게임 횟수
    Long countByUser_Id(Long userId);

    // 사용자별 완료된 게임 횟수
    Long countByUser_IdAndStatus(Long userId, GameSessionStatus status);

    // 사용자별 최근 게임 기록 조회 (N개, Song 정보 포함)
    @Query("SELECT gr FROM GameResult gr LEFT JOIN FETCH gr.song WHERE gr.user.id = :userId ORDER BY gr.startTime DESC")
    List<GameResult> findRecentGamesByUserId(@Param("userId") Long userId);

    // 사용자별 특정 기간 내 게임 기록 조회
    @Query("SELECT gr FROM GameResult gr WHERE gr.user.id = :userId AND gr.startTime >= :startDate AND gr.startTime <= :endDate ORDER BY gr.startTime DESC")
    List<GameResult> findByUser_IdAndStartTimeBetween(@Param("userId") Long userId,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    // 사용자별 ScoreByAction 포함 조회
    @Query("SELECT gr FROM GameResult gr LEFT JOIN FETCH gr.scoresByAction WHERE gr.user.id = :userId")
    List<GameResult> findByUser_IdWithScores(@Param("userId") Long userId);

}