package com.heungbuja.song.repository;

import com.heungbuja.song.entity.ListeningHistory;
import com.heungbuja.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListeningHistoryRepository extends JpaRepository<ListeningHistory, Long> {

    /**
     * 사용자의 최근 청취 이력 조회
     */
    List<ListeningHistory> findByUserOrderByPlayedAtDesc(User user);

    /**
     * 사용자의 최근 N개 청취 이력 조회
     */
    List<ListeningHistory> findTop10ByUserOrderByPlayedAtDesc(User user);
}
