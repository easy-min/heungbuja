package com.heungbuja.context.repository;

import com.heungbuja.context.entity.ConversationContext;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ConversationContext Redis Repository
 */
@Repository
public interface ConversationContextRepository extends CrudRepository<ConversationContext, String> {

    /**
     * 사용자 ID로 컨텍스트 조회
     */
    Optional<ConversationContext> findByUserId(Long userId);

    /**
     * 사용자 ID로 컨텍스트 삭제
     */
    void deleteByUserId(Long userId);
}
