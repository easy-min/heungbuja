package com.heungbuja.game.repository;

import com.heungbuja.game.domain.GameDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

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
}
