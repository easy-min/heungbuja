package com.heungbuja.song.repository.mongo;

import com.heungbuja.song.domain.ChoreographyPattern;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

/**
 * ChoreographyPattern Document를 위한 Repository 인터페이스
 */
public interface ChoreographyPatternRepository extends MongoRepository<ChoreographyPattern, String> {

    /**
     * songId를 사용하여 해당 노래의 전체 동작 패턴 데이터를 조회합니다.
     * @param songId MySQL의 Song.id
     * @return 해당 노래의 ChoreographyPattern 객체
     */
    Optional<ChoreographyPattern> findBySongId(Long songId);
}