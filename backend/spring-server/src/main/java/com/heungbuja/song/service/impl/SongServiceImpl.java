package com.heungbuja.song.service.impl;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.jpa.SongRepository;
import com.heungbuja.song.service.RedisSongCacheService;
import com.heungbuja.song.service.SongService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceImpl implements SongService {

    private final SongRepository songRepository;
    private final RedisSongCacheService redisCacheService;
    private final Random random = new Random();

    @Override
    public Song searchSong(String query) {
        List<Song> results = songRepository.searchByQuery(query);

        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.SONG_NOT_FOUND,
                    "'" + query + "' 검색 결과가 없습니다");
        }

        // 랜덤으로 1곡 선택
        return results.get(random.nextInt(results.size()));
    }

    @Override
    public Song searchByArtist(String artist) {
        log.info("🔍 가수 검색 시작: '{}'", artist);

        // [1단계] FULLTEXT 검색
        List<Song> results = songRepository.fullTextSearch(artist, 10);
        if (!results.isEmpty()) {
            log.info("✅ [FULLTEXT] {} 곡 발견", results.size());
            return selectBest(results);
        }

        // [2단계] Redis 캐시에서 정확 매칭
        List<Song> allSongs = redisCacheService.getAllSongs();
        results = allSongs.stream()
            .filter(song -> containsIgnoreCase(song.getArtist(), artist))
            .toList();

        if (!results.isEmpty()) {
            log.info("✅ [Redis 정확 매칭] {} 곡 발견", results.size());
            return selectBest(results);
        }

        // [3단계] 띄어쓰기 제거 검색
        String artistNoSpace = artist.replaceAll("\\s+", "");
        results = allSongs.stream()
            .filter(song -> containsIgnoreCase(song.getArtist(), artistNoSpace))
            .toList();

        if (!results.isEmpty()) {
            log.info("✅ [띄어쓰기 제거] {} 곡 발견", results.size());
            return selectBest(results);
        }

        // [4단계] DB LIKE 검색
        results = songRepository.findByArtistContaining(artist);
        if (!results.isEmpty()) {
            log.info("✅ [DB LIKE] {} 곡 발견", results.size());
            return selectBest(results);
        }

        log.error("❌ 가수 검색 실패: '{}'", artist);
        throw new CustomException(ErrorCode.SONG_NOT_FOUND,
                "'" + artist + "' 가수의 노래를 찾을 수 없습니다");
    }

    @Override
    public Song searchByTitle(String title) {
        log.info("🔍 제목 검색 시작: '{}'", title);

        // [1단계] FULLTEXT 검색 시도 (가장 빠르고 정확)
        List<Song> results = songRepository.fullTextSearch(title, 10);
        if (!results.isEmpty()) {
            log.info("✅ [FULLTEXT] {} 곡 발견", results.size());
            return selectBest(results);
        }
        log.info("⚠️ [FULLTEXT] 검색 실패, 폴백 검색 시작...");

        // [2단계] Redis 캐시에서 전체 곡 조회 후 contains 검색
        List<Song> allSongs = redisCacheService.getAllSongs();
        results = allSongs.stream()
            .filter(song -> containsIgnoreCase(song.getTitle(), title))
            .toList();

        if (!results.isEmpty()) {
            log.info("✅ [Redis 정확 매칭] {} 곡 발견", results.size());
            return selectBest(results);
        }

        // [3단계] 띄어쓰기 제거 검색
        String titleNoSpace = title.replaceAll("\\s+", "");
        results = allSongs.stream()
            .filter(song -> containsIgnoreCase(song.getTitle(), titleNoSpace))
            .toList();

        if (!results.isEmpty()) {
            log.info("✅ [띄어쓰기 제거 '{}'] {} 곡 발견", titleNoSpace, results.size());
            return selectBest(results);
        }

        // [4단계] 첫 단어만 검색
        if (title.contains(" ")) {
            String firstWord = title.split("\\s+")[0];
            if (firstWord.length() >= 2) {
                results = allSongs.stream()
                    .filter(song -> containsIgnoreCase(song.getTitle(), firstWord))
                    .toList();

                if (!results.isEmpty()) {
                    log.info("✅ [첫 단어 '{}'] {} 곡 발견", firstWord, results.size());
                    return selectBest(results);
                }
            }
        }

        // [5단계] DB LIKE 검색 (최후의 수단)
        results = songRepository.findByTitleContaining(title);
        if (!results.isEmpty()) {
            log.info("✅ [DB LIKE] {} 곡 발견", results.size());
            return selectBest(results);
        }

        log.error("❌ 모든 검색 실패: '{}'", title);
        throw new CustomException(ErrorCode.SONG_NOT_FOUND,
                "'" + title + "' 제목의 노래를 찾을 수 없습니다");
    }

    @Override
    public Song searchByArtistAndTitle(String artist, String title) {
        log.info("🔍 가수+제목 검색: artist='{}', title='{}'", artist, title);

        // [1단계] FULLTEXT 검색 (조합)
        String query = artist + " " + title;
        List<Song> results = songRepository.fullTextSearch(query, 10);
        if (!results.isEmpty()) {
            log.info("✅ [FULLTEXT 조합] {} 곡 발견", results.size());
            return selectBest(results);
        }

        // [2단계] Redis 캐시에서 정확 매칭
        List<Song> allSongs = redisCacheService.getAllSongs();
        results = allSongs.stream()
            .filter(song ->
                containsIgnoreCase(song.getArtist(), artist) &&
                containsIgnoreCase(song.getTitle(), title))
            .toList();

        if (!results.isEmpty()) {
            log.info("✅ [Redis 정확 매칭] {} 곡 발견", results.size());
            return selectBest(results);
        }

        // [3단계] DB LIKE 검색
        results = songRepository.findByArtistAndTitle(artist, title);
        if (!results.isEmpty()) {
            log.info("✅ [DB LIKE] {} 곡 발견", results.size());
            return selectBest(results);
        }

        // [4단계] 제목만으로 검색 (폴백)
        log.info("⚠️ 가수+제목 매칭 실패, 제목만으로 재시도");
        return searchByTitle(title);
    }

    @Override
    public Song findById(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new CustomException(ErrorCode.SONG_NOT_FOUND,
                        "노래를 찾을 수 없습니다 (ID: " + songId + ")"));
    }

    // ========== 헬퍼 메서드 ==========

    /**
     * 검색 결과 중 최적의 곡 선택
     * - 1곡이면 바로 반환
     * - 여러 곡이면 랜덤 선택
     */
    private Song selectBest(List<Song> results) {
        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.SONG_NOT_FOUND);
        }

        if (results.size() == 1) {
            return results.get(0);
        }

        // 여러 곡이면 랜덤 선택 (20곡 정도면 랭킹 불필요)
        Song selected = results.get(random.nextInt(results.size()));
        log.debug("🎲 {}곡 중 랜덤 선택: [{}] {} - {}",
            results.size(), selected.getId(), selected.getArtist(), selected.getTitle());
        return selected;
    }

    /**
     * 대소문자 무시 contains 검사
     */
    private boolean containsIgnoreCase(String source, String target) {
        if (source == null || target == null) {
            return false;
        }
        return source.toLowerCase().contains(target.toLowerCase());
    }
}
