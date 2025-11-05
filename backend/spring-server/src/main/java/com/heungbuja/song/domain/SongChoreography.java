package com.heungbuja.song.domain;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * MongoDB의 'song_choreographies' 컬렉션과 매핑되는 Document 클래스
 * 노래의 안무 정보를 담고 있음
 */
@Getter
@Document(collection = "song_choreographies")
public class SongChoreography {

    @Id
    private String id;
    private String song; // 노래 제목

    private List<Version> versions;

    @Getter
    public static class Version {
        private String id; // "v_a_b1", "v_a_b2" 등
        private String desc;
        private List<Action> actions;
    }

    @Getter
    public static class Action {
        private String section; // "verse1", "verse2"
        private String action;  // "a", "b1", "b2" 등
        private String unit;
        private List<Block> blocks;
    }

    @Getter
    public static class Block {
        private int bar_start;
        private int bar_end;
        private int beat_start;
        private int beat_end;
        private double t_start;
        private double t_end;
    }
}