package com.heungbuja.song.service;

import com.heungbuja.song.entity.ListeningHistory;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.enums.PlaybackMode;
import com.heungbuja.user.entity.User;

import java.util.List;

/**
 * 청취 이력 관리 서비스 인터페이스
 * 프론트가 음악 재생을 관리하므로, 백엔드는 "어떤 곡을 들었는지" 이력만 기록
 */
public interface ListeningHistoryService {

    /**
     * 청취 이력 기록 (노래 재생 시)
     */
    ListeningHistory recordListening(User user, Song song, PlaybackMode mode);

    /**
     * 사용자의 최근 청취 이력 조회
     */
    List<ListeningHistory> getRecentHistory(User user, int limit);
}
