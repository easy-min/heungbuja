package com.heungbuja.song.repository;

import com.heungbuja.song.domain.SongChoreography;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SongChoreographyRepository extends MongoRepository<SongChoreography, String> {

    Optional<SongChoreography> findBySongId(Long songId);
}