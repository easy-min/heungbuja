package com.heungbuja.voice.repository;

import com.heungbuja.voice.entity.VoiceCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VoiceCommandRepository extends JpaRepository<VoiceCommand, Long> {

    // 특정 어르신의 음성 명령 조회
    List<VoiceCommand> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 특정 기간의 음성 명령 조회
    List<VoiceCommand> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    // 특정 어르신의 최근 N개 음성 명령 조회
    List<VoiceCommand> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);
}
