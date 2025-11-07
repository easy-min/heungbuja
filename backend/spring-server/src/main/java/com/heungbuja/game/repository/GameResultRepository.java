package com.heungbuja.game.repository;

import com.heungbuja.game.entity.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    // (예시) 나중에 특정 유저의 모든 게임 기록을 조회할 때 사용할 수 있는 쿼리 메소드
    List<GameResult> findByUser_IdOrderByPlayedAtDesc(Long userId);

}