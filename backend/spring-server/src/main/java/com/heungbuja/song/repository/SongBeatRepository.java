package com.heungbuja.song.repository;

import com.heungbuja.song.domain.SongBeat;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

// MongoRepository<도큐먼트 클래스, ID 필드의 타입> 을 상속받습니다.
public interface SongBeatRepository extends MongoRepository<SongBeat, String> {

    Optional<SongBeat> findBySongId(Long songId);
}