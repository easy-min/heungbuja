package com.heungbuja.song.repository;

import com.heungbuja.song.domain.ChoreographyPattern;
import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.repository.mongo.ChoreographyPatternRepository;
import com.heungbuja.song.repository.mongo.SongBeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
//import lombok.RequiredArgsConstructor;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class MongoMappingTest {

    @Autowired
    private ChoreographyPatternRepository choreographyPatternRepository;

    @Autowired
    private SongBeatRepository songBeatRepository;

    @Test
    @DisplayName("MongoDB의 choreography_patterns 문서를 Java 객체로 완벽하게 매핑할 수 있다.")
    void choreographyPatternMappingTest() {
        System.out.println("--- choreographyPatternMappingTest 시작 ---");

        // 1. DB에서 songId=1인 데이터를 직접 조회
        ChoreographyPattern patternData = choreographyPatternRepository.findBySongId(1L)
                .orElse(null);

        // 2. 가장 중요한 검증: patternData 객체와 그 내부 리스트가 null이 아닌지 확인
        assertThat(patternData).as("ChoreographyPattern 객체가 DB에 존재해야 합니다.").isNotNull();
        System.out.println(" > ChoreographyPattern 객체 조회 성공!");

        assertThat(patternData.getPatterns()).as("patterns 리스트는 null이 아니어야 합니다.").isNotNull();
        System.out.println(" > getPatterns()는 null이 아닙니다!");

        assertThat(patternData.getPatterns()).as("patterns 리스트는 비어있지 않아야 합니다.").isNotEmpty();
        System.out.println(" > patterns 리스트는 비어있지 않습니다! 크기: " + patternData.getPatterns().size());

        // 3. 리스트의 첫 번째 요소 내부 필드들이 null이 아닌지 확인
        ChoreographyPattern.Pattern firstPattern = patternData.getPatterns().get(0);
        assertThat(firstPattern).as("리스트의 첫 번째 Pattern 객체는 null이 아니어야 합니다.").isNotNull();
        System.out.println(" > 첫 번째 Pattern 객체 조회 성공!");

        assertThat(firstPattern.getId()).as("첫 번째 Pattern의 id는 null이 아니어야 합니다.").isNotNull();
        System.out.println(" > 첫 번째 Pattern의 id: " + firstPattern.getId());

        assertThat(firstPattern.getSequence()).as("첫 번째 Pattern의 sequence는 null이 아니어야 합니다.").isNotNull();
        System.out.println(" > 첫 번째 Pattern의 sequence 크기: " + firstPattern.getSequence().size());

        System.out.println("--- choreographyPatternMappingTest 성공! ---");
    }

    @Test
    @DisplayName("MongoDB의 song_beats 문서를 Java 객체로 완벽하게 매핑할 수 있다.")
    void songBeatMappingTest() {
        System.out.println("--- songBeatMappingTest 시작 ---");
        SongBeat songBeat = songBeatRepository.findBySongId(1L).orElse(null);

        assertThat(songBeat).as("SongBeat 객체가 DB에 존재해야 합니다.").isNotNull();
        assertThat(songBeat.getSections()).as("sections 리스트는 null이 아니어야 합니다.").isNotNull();
        assertThat(songBeat.getSections()).as("sections 리스트는 비어있지 않아야 합니다.").isNotEmpty();

        SongBeat.Section firstSection = songBeat.getSections().get(0);
        assertThat(firstSection.getLabel()).as("첫 번째 Section의 label은 null이 아니어야 합니다.").isNotNull();
        System.out.println(" > 첫 번째 Section의 label: " + firstSection.getLabel());

        System.out.println("--- songBeatMappingTest 성공! ---");
    }
}