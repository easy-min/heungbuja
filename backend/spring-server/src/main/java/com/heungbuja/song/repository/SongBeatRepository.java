package com.heungbuja.song.repository;

import com.heungbuja.song.domain.SongBeat;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

// MongoRepository<도큐먼트 클래스, ID 필드의 타입> 을 상속받습니다.
public interface SongBeatRepository extends MongoRepository<SongBeat, String> {

    // "audio.title" 필드로 SongBeat 도큐먼트를 찾는 메소드를 직접 정의할 수 있습니다.
    // Spring Data가 메소드 이름을 분석해서 자동으로 쿼리를 만들어줍니다.
    // 'findBy' + '필드이름' 규칙을 따릅니다. 중첩된 객체는 'AudioTitle' 처럼 이어붙입니다.
    Optional<SongBeat> findByAudioTitle(String title);
}