package com.heungbuja.game.repository.jpa;

import com.heungbuja.game.entity.ScoreByAction;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository를 상속받아 기본적인 CRUD 기능을 자동으로 구현합니다.
public interface ScoreByActionRepository extends JpaRepository<ScoreByAction, Long> {
    // 지금 당장은 특별한 조회 메소드가 필요 없으므로, 내용은 비워둡니다.
    // 나중에 특정 게임 결과에 속한 점수들만 조회하고 싶다면 아래와 같은 메소드를 추가할 수 있습니다.
    // List<ScoreByAction> findAllByGameResult_Id(Long gameResultId);
}