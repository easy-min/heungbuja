package com.heungbuja.song.repository.mongo;

import com.heungbuja.song.domain.SongLyrics;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SongLyricsRepository extends MongoRepository<SongLyrics, String> {

    Optional<SongLyrics> findBySongId(Long songId);
}