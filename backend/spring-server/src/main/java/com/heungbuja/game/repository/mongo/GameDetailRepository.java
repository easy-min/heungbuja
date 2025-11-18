package com.heungbuja.game.repository.mongo;

import com.heungbuja.game.domain.GameDetail;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 게임 상세 데이터 Repository (MongoDB)
 */
public interface GameDetailRepository extends MongoRepository<GameDetail, String> {

    /**
     * 세션 ID로 게임 상세 데이터 조회
     */
    Optional<GameDetail> findBySessionId(String sessionId);

    /**
     * 세션 ID로 게임 상세 데이터 삭제
     */
    void deleteBySessionId(String sessionId);

    /**
     * 여러 세션 ID로 게임 상세 데이터 조회 (사용자별 통계용)
     */
    List<GameDetail> findBySessionIdIn(List<String> sessionIds);
}
