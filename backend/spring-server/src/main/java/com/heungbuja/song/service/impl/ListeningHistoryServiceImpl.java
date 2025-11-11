package com.heungbuja.song.service.impl;

import com.heungbuja.song.entity.ListeningHistory;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.enums.PlaybackMode;
import com.heungbuja.song.repository.jpa.ListeningHistoryRepository;
import com.heungbuja.song.service.ListeningHistoryService;
import com.heungbuja.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 청취 이력 관리 서비스 구현체
 * 프론트가 음악 재생을 관리하므로, 백엔드는 "어떤 곡을 들었는지" 이력만 기록
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListeningHistoryServiceImpl implements ListeningHistoryService {

    private final ListeningHistoryRepository listeningHistoryRepository;

    @Override
    @Transactional
    public ListeningHistory recordListening(User user, Song song, PlaybackMode mode) {
        ListeningHistory history = ListeningHistory.builder()
                .user(user)
                .song(song)
                .mode(mode)
                .build();

        log.info("청취 이력 저장: userId={}, songId={}, mode={}", user.getId(), song.getId(), mode);
        return listeningHistoryRepository.save(history);
    }

    @Override
    public List<ListeningHistory> getRecentHistory(User user, int limit) {
        if (limit <= 10) {
            return listeningHistoryRepository.findTop10ByUserOrderByPlayedAtDesc(user);
        }
        return listeningHistoryRepository.findByUserOrderByPlayedAtDesc(user);
    }
}
