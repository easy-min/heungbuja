package com.heungbuja.song.repository;

import com.heungbuja.song.domain.SongBeat;
import com.heungbuja.song.domain.SongChoreography;
import com.heungbuja.song.domain.SongLyrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest // Spring Boot의 모든 설정을 불러와서 통합 테스트를 진행
@ActiveProfiles("test")
class SongMongoRepositoryTest {

    @Autowired // 테스트할 Repository들을 Spring 컨테이너에서 주입받음
    private SongBeatRepository songBeatRepository;

    @Autowired
    private SongLyricsRepository songLyricsRepository;

    @Autowired
    private SongChoreographyRepository songChoreographyRepository;

    // 테스트할 노래의 정확한 제목 (JSON 파일에 있는 그대로)
    private final String songTitle = "[Official Audio] 서주경(Seo Joo Kyung) - 당돌한 여자.mp3";
    private final long songId = 10;

    @Test
    @DisplayName("MongoDB에서 노래 비트 정보를 제목으로 성공적으로 조회한다.")
    void findSongBeatByTitleTest() {
        // when: findByAudioTitle 메소드를 호출했을 때
        Optional<SongBeat> songBeatOptional = songBeatRepository.findBySongId(songId);

        // then: 결과가 존재해야 하며, 제목이 일치해야 한다.
        assertThat(songBeatOptional).isPresent(); // Optional 객체가 비어있지 않은지 확인
        SongBeat songBeat = songBeatOptional.get();
        assertThat(songBeat.getAudio().getTitle()).isEqualTo(songTitle);
        System.out.println("SongBeat 조회 성공: " + songBeat.getAudio().getTitle());
    }

    @Test
    @DisplayName("MongoDB에서 노래 가사 정보를 제목으로 성공적으로 조회한다.")
    void findSongLyricsByTitleTest() {
        // when: findByTitle 메소드를 호출했을 때
        Optional<SongLyrics> songLyricsOptional = songLyricsRepository.findBySongId(songId);

        // then: 결과가 존재해야 하며, 첫 번째 가사가 비어있지 않아야 한다.
        assertThat(songLyricsOptional).isPresent();
        SongLyrics songLyrics = songLyricsOptional.get();
        assertThat(songLyrics.getLines().get(0).getText()).isEqualTo("일부러 안 웃는거 맞죠");
        System.out.println("SongLyrics 조회 성공: 첫 가사 - " + songLyrics.getLines().get(0).getText());
    }

    @Test
    @DisplayName("MongoDB에서 노래 안무 정보를 제목으로 성공적으로 조회한다.")
    void findSongChoreographyByTitleTest() {
        // when: findBySong 메소드를 호출했을 때
        Optional<SongChoreography> songChoreographyOptional = songChoreographyRepository.findBySongId(songId);

        // then: 결과가 존재해야 하며, 버전 정보가 비어있지 않아야 한다.
        assertThat(songChoreographyOptional).isPresent();
        SongChoreography choreography = songChoreographyOptional.get();
        assertThat(choreography.getVersions()).isNotEmpty();
        System.out.println("SongChoreography 조회 성공: 버전 ID - " + choreography.getVersions().get(0).getId());
    }
}