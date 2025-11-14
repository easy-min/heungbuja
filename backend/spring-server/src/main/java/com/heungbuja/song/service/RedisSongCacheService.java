package com.heungbuja.song.service;

import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.jpa.SongRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redis를 활용한 Song 캐시 서비스
 * - 전체 곡 정보를 Redis Hash에 저장 (20곡 정도면 0.1MB 이하)
 * - 애플리케이션 시작 시 자동 로드
 * - DB 조회 없이 Redis에서 빠르게 조회
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisSongCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SongRepository songRepository;

    private static final String CACHE_KEY = "songs:cache:all";

    /**
     * 애플리케이션 시작 시 전체 곡을 Redis에 로드
     */
    @PostConstruct
    public void loadAllSongsToRedis() {
        log.info("🎵 Redis 노래 캐시 초기화 시작...");

        List<Song> allSongs = songRepository.findAll();

        if (allSongs.isEmpty()) {
            log.warn("⚠️ DB에 노래가 없습니다");
            return;
        }

        // Redis Hash에 저장: songId → Song 객체
        Map<String, Song> cacheMap = allSongs.stream()
            .collect(Collectors.toMap(
                song -> song.getId().toString(),
                song -> song
            ));

        redisTemplate.opsForHash().putAll(CACHE_KEY, cacheMap);

        log.info("✅ Redis 노래 캐시 초기화 완료: {} 곡", allSongs.size());
    }

    /**
     * Redis에서 전체 곡 조회
     * @return 전체 Song 리스트
     */
    public List<Song> getAllSongs() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(CACHE_KEY);

        if (entries.isEmpty()) {
            log.warn("⚠️ Redis 캐시가 비어있음, DB에서 재로드...");
            loadAllSongsToRedis();
            entries = redisTemplate.opsForHash().entries(CACHE_KEY);
        }

        return entries.values().stream()
            .map(obj -> (Song) obj)
            .toList();
    }

    /**
     * 특정 곡 조회 (by ID)
     */
    public Song getSongById(Long songId) {
        Object result = redisTemplate.opsForHash().get(CACHE_KEY, songId.toString());
        return result != null ? (Song) result : null;
    }

    /**
     * 곡 추가/수정 시 Redis 캐시 갱신
     */
    public void refreshSong(Song song) {
        redisTemplate.opsForHash().put(CACHE_KEY, song.getId().toString(), song);
        log.info("🔄 Redis 캐시 갱신: songId={}, title={}", song.getId(), song.getTitle());
    }

    /**
     * 곡 삭제 시 Redis 캐시에서 제거
     */
    public void removeSong(Long songId) {
        redisTemplate.opsForHash().delete(CACHE_KEY, songId.toString());
        log.info("🗑️ Redis 캐시 삭제: songId={}", songId);
    }

    /**
     * 전체 캐시 무효화 및 재로드 (수동 호출용)
     */
    public void invalidateAndReload() {
        redisTemplate.delete(CACHE_KEY);
        loadAllSongsToRedis();
        log.info("🔄 Redis 캐시 전체 재로드 완료");
    }
}
