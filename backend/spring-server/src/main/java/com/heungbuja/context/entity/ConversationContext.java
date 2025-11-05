package com.heungbuja.context.entity;

import com.heungbuja.song.enums.PlaybackMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 대화 컨텍스트 (Redis 세션 저장)
 *
 * 사용자의 현재 상태를 Redis에 저장하여 GPT가 컨텍스트를 이해하도록 함:
 * - 현재 모드 (HOME, LISTENING, EXERCISE)
 * - 재생 중인 곡
 * - 대기열 (playlist queue)
 * - 마지막 상호작용 시각
 *
 * TTL: 30분 (1800초) - 30분간 상호작용이 없으면 세션 만료
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash(value = "conversation_context")
public class ConversationContext {

    /**
     * Redis Key: user:{userId}
     */
    @Id
    private String id;  // "user:{userId}" 형식

    /**
     * 사용자 ID
     */
    private Long userId;

    /**
     * 현재 모드 (HOME, LISTENING, EXERCISE)
     */
    @Builder.Default
    private PlaybackMode currentMode = PlaybackMode.HOME;

    /**
     * 현재 재생 중인 곡 ID
     */
    private Long currentSongId;

    /**
     * 대기열 (재생 예정 곡 ID 리스트)
     */
    @Builder.Default
    private List<Long> playlistQueue = new ArrayList<>();

    /**
     * 마지막 상호작용 시각
     */
    @Builder.Default
    private LocalDateTime lastInteractionAt = LocalDateTime.now();

    /**
     * TTL (Time To Live): 30분 = 1800초
     * 30분간 상호작용이 없으면 Redis에서 자동 삭제
     */
    @TimeToLive
    @Builder.Default
    private Long ttl = 1800L;

    /**
     * Redis Key 생성 헬퍼
     */
    public static String createKey(Long userId) {
        return "user:" + userId;
    }

    /**
     * 모드 변경
     */
    public void changeMode(PlaybackMode newMode) {
        this.currentMode = newMode;
        this.lastInteractionAt = LocalDateTime.now();
    }

    /**
     * 현재 재생 곡 설정
     */
    public void setCurrentSong(Long songId) {
        this.currentSongId = songId;
        this.lastInteractionAt = LocalDateTime.now();
    }

    /**
     * 대기열에 곡 추가
     */
    public void addToQueue(Long songId) {
        if (this.playlistQueue == null) {
            this.playlistQueue = new ArrayList<>();
        }
        this.playlistQueue.add(songId);
        this.lastInteractionAt = LocalDateTime.now();
    }

    /**
     * 대기열에서 다음 곡 가져오기 (pop)
     */
    public Long pollNextSong() {
        if (this.playlistQueue == null || this.playlistQueue.isEmpty()) {
            return null;
        }
        this.lastInteractionAt = LocalDateTime.now();
        return this.playlistQueue.remove(0);
    }

    /**
     * 대기열 초기화
     */
    public void clearQueue() {
        if (this.playlistQueue != null) {
            this.playlistQueue.clear();
        }
        this.lastInteractionAt = LocalDateTime.now();
    }

    /**
     * 상호작용 시각 업데이트
     */
    public void touch() {
        this.lastInteractionAt = LocalDateTime.now();
    }
}
