package com.heungbuja.song.service.impl;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.repository.SongRepository;
import com.heungbuja.song.service.SongService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

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
        List<Song> results = songRepository.findByArtistContaining(artist);

        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.SONG_NOT_FOUND,
                    artist + " 가수의 노래를 찾을 수 없습니다");
        }

        return results.get(random.nextInt(results.size()));
    }

    @Override
    public Song searchByTitle(String title) {
        List<Song> results = songRepository.findByTitleContaining(title);

        if (results.isEmpty()) {
            throw new CustomException(ErrorCode.SONG_NOT_FOUND,
                    "'" + title + "' 제목의 노래를 찾을 수 없습니다");
        }

        return results.get(random.nextInt(results.size()));
    }

    @Override
    public Song searchByArtistAndTitle(String artist, String title) {
        List<Song> results = songRepository.findByArtistAndTitle(artist, title);

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
