package com.heungbuja.song.repository;

import com.heungbuja.song.domain.SongLyrics;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SongLyricsRepository extends MongoRepository<SongLyrics, String> {

    // 'title' 필드로 SongLyrics 도큐먼트를 찾는 쿼리 메소드
    Optional<SongLyrics> findByTitle(String title);
}