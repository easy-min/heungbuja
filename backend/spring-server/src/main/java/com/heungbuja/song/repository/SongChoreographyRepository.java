package com.heungbuja.song.repository;

import com.heungbuja.song.domain.SongChoreography;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SongChoreographyRepository extends MongoRepository<SongChoreography, String> {

    // 'song' 필드(노래 제목)로 SongChoreography 도큐먼트를 찾는 쿼리 메소드
    Optional<SongChoreography> findBySong(String songTitle);
}