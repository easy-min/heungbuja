package com.heungbuja.song.domain;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

/**
 * MongoDB의 'song_beats' 컬렉션과 매핑되는 Document 클래스
 * 노래의 비트, 섹션 등 시간 정보를 담고 있음
 */
@Getter
@Document(collection = "song_beats") // MongoDB의 컬렉션 이름을 지정
public class SongBeat {

    @Id
    private String id; // MongoDB의 고유 ID인 "_id"와 매핑됩니다.

    private Audio audio;
    private List<Beat> beats;
    private List<Section> sections;

    // JSON 내부의 객체들은 내부 클래스로 표현하면 편리합니다.
    @Getter
    public static class Audio {
        private String title;
        private double durationSec;
    }

    @Getter
    public static class Beat {
        private int i;
        private int bar;
        private int beat;
        private double t; // 비트의 시간 (초)
    }

    @Getter
    public static class Section {
        private String label; // "intro", "part1", "part2" 등
        private int startBeat;
        private int endBeat;
        private int startBar;
        private int endBar;
    }
}