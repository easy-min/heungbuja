package com.heungbuja.game.repository;

import com.heungbuja.game.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ActionRepository extends JpaRepository<Action, Long> {

    // actionCode로 동작 정보를 쉽게 찾기 위한 쿼리 메소드
    Optional<Action> findByActionCode(int actionCode);
}