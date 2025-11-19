package com.heungbuja.song.service;

import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.jpa.SongRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis를 활용한 Song 캐시 서비스 (간단 버전)
 * - Song ID 리스트만 Redis에 저장
 * - 실제 Song은 DB에서 조회 (20곡이라 빠름)
 * - 캐시는 "곡이 존재하는지" 확인 용도로만 사용
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisSongCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SongRepository songRepository;

    private static final String CACHE_KEY = "songs:ids:all";

    /**
     * 애플리케이션 시작 시 전체 곡 ID를 Redis에 로드
     */
    @PostConstruct
    public void loadAllSongsToRedis() {
        log.info("🎵 Redis 노래 ID 캐시 초기화 시작...");

        try {
            List<Song> allSongs = songRepository.findAll();

            if (allSongs.isEmpty()) {
                log.warn("⚠️ DB에 노래가 없습니다");
                return;
            }

            // Redis Set에 songId 저장
            Set<Long> songIds = allSongs.stream()
                .map(Song::getId)
                .collect(Collectors.toSet());

            redisTemplate.delete(CACHE_KEY);
            redisTemplate.opsForSet().add(CACHE_KEY, songIds.toArray());

            log.info("✅ Redis 노래 ID 캐시 초기화 완료: {} 곡", songIds.size());
        } catch (Exception e) {
            log.error("❌ Redis 캐시 초기화 실패, 계속 진행합니다", e);
        }
    }

    /**
     * DB에서 전체 곡 조회 (20곡이라 빠름)
     */
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    /**
     * 특정 곡이 존재하는지 확인
     */
    public boolean existsSong(Long songId) {
        Boolean isMember = redisTemplate.opsForSet().isMember(CACHE_KEY, songId);
        return isMember != null && isMember;
    }

    /**
     * 곡 추가 시 Redis 캐시 갱신
     */
    public void addSong(Long songId) {
        redisTemplate.opsForSet().add(CACHE_KEY, songId);
        log.info("🔄 Redis 캐시 추가: songId={}", songId);
    }

    /**
     * 곡 삭제 시 Redis 캐시에서 제거
     */
    public void removeSong(Long songId) {
        redisTemplate.opsForSet().remove(CACHE_KEY, songId);
        log.info("🗑️ Redis 캐시 삭제: songId={}", songId);
    }

    /**
     * 전체 캐시 무효화 및 재로드
     */
    public void invalidateAndReload() {
        loadAllSongsToRedis();
        log.info("🔄 Redis 캐시 전체 재로드 완료");
    }
}
