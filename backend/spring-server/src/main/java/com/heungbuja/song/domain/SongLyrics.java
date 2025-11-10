package com.heungbuja.song.domain;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.List;

/**
 * MongoDB의 'song_lyrics' 컬렉션과 매핑되는 Document 클래스
 * 노래의 가사 정보를 라인별로 담고 있음
 */
@Getter
@Document(collection = "song_lyrics")
public class SongLyrics {

    @Id
    private String id;
    private Long songId;

    private String title;

    private List<Line> lines;

    @Getter
    public static class Line {
        private int lineIndex;
        private String text;
        private double start; // 시작 시간 (초)
        private double end;   // 종료 시간 (초)
        private int sBeat; // 시작 비트
        private int eBeat; // 종료 비트
    }
}