package com.heungbuja.song.service.impl;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.jpa.SongRepository;
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
        log.info("가수 검색 시작: '{}'", artist);

        // 1. 원본 그대로 검색
        List<Song> results = songRepository.findByArtistContaining(artist);
        log.info("1단계 (원본 검색): {} 곡", results.size());

        // 2. 띄어쓰기 제거하고 검색
        if (results.isEmpty()) {
            String artistNoSpace = artist.replaceAll("\\s+", "");
            results = songRepository.findByArtistContaining(artistNoSpace);
            log.info("2단계 (띄어쓰기 제거 '{}' 검색): {} 곡", artistNoSpace, results.size());
        }

        // 3. 첫 단어만으로 검색
        if (results.isEmpty() && artist.contains(" ")) {
            String firstWord = artist.split("\\s+")[0];
            if (firstWord.length() >= 2) {
                results = songRepository.findByArtistContaining(firstWord);
                log.info("3단계 (첫 단어 '{}' 검색): {} 곡", firstWord, results.size());
            }
        }

        log.info("최종 검색 결과: {} 곡", results.size());

        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.SONG_NOT_FOUND,
                    artist + " 가수의 노래를 찾을 수 없습니다");
        }

        return results.get(random.nextInt(results.size()));
    }

    @Override
    public Song searchByTitle(String title) {
        log.info("제목 검색 시작: '{}'", title);

        // 1. 원본 그대로 검색
        List<Song> results = songRepository.findByTitleContaining(title);
        log.info("1단계 (원본 검색): {} 곡", results.size());

        // 2. 띄어쓰기 제거하고 검색
        if (results.isEmpty()) {
            String titleNoSpace = title.replaceAll("\\s+", "");
            results = songRepository.findByTitleContaining(titleNoSpace);
            log.info("2단계 (띄어쓰기 제거 '{}' 검색): {} 곡", titleNoSpace, results.size());
        }

        // 3. 첫 단어만으로 검색 (예: "당돌한 여자" → "당돌한")
        if (results.isEmpty() && title.contains(" ")) {
            String firstWord = title.split("\\s+")[0];
            if (firstWord.length() >= 2) {  // 최소 2글자 이상
                results = songRepository.findByTitleContaining(firstWord);
                log.info("3단계 (첫 단어 '{}' 검색): {} 곡", firstWord, results.size());
            }
        }

        // 4. 각 단어를 포함하는 모든 곡 검색 (OR 조건)
        if (results.isEmpty()) {
            String[] words = title.split("\\s+");
            for (String word : words) {
                if (word.length() >= 2) {
                    List<Song> wordResults = songRepository.findByTitleContaining(word);
                    results.addAll(wordResults);
                    log.info("4단계 (단어 '{}' 검색): +{} 곡", word, wordResults.size());
                }
            }
        }

        log.info("최종 검색 결과: {} 곡", results.size());
        if (!results.isEmpty()) {
            results.forEach(song -> log.info("  - [{}] {}", song.getId(), song.getTitle()));
        }

        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.SONG_NOT_FOUND,
                    "'" + title + "' 제목의 노래를 찾을 수 없습니다");
        }

        return results.get(random.nextInt(results.size()));
    }

    @Override
    public Song searchByArtistAndTitle(String artist, String title) {
        log.info("가수+제목 검색 시작: artist='{}', title='{}'", artist, title);

        // 1. 둘 다 원본 그대로 검색
        List<Song> results = songRepository.findByArtistAndTitle(artist, title);
        log.info("1단계 (원본): {} 곡", results.size());

        // 2. 둘 다 띄어쓰기 제거
        if (results.isEmpty()) {
            String artistNoSpace = artist.replaceAll("\\s+", "");
            String titleNoSpace = title.replaceAll("\\s+", "");
            results = songRepository.findByArtistAndTitle(artistNoSpace, titleNoSpace);
            log.info("2단계 (띄어쓰기 제거): {} 곡", results.size());
        }

        // 3. 제목만으로 검색 (가수가 정확히 안 맞을 수 있음)
        if (results.isEmpty()) {
            log.info("3단계: 제목만으로 검색 시도");
            return searchByTitle(title);
        }

        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.SONG_NOT_FOUND,
                    artist + "의 '" + title + "' 노래를 찾을 수 없습니다");
        }

        return results.get(random.nextInt(results.size()));
    }

    @Override
    public Song findById(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new CustomException(ErrorCode.SONG_NOT_FOUND,
                        "노래를 찾을 수 없습니다 (ID: " + songId + ")"));
    }
}
