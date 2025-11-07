package com.heungbuja.song.repository;

import com.heungbuja.song.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SongRepository extends JpaRepository<Song, Long> {

    // 가수명으로 검색
    List<Song> findByArtistContaining(String artist);

    // 곡 제목으로 검색
    List<Song> findByTitleContaining(String title);

    // 가수명 + 곡 제목으로 검색
    @Query("SELECT s FROM Song s WHERE s.artist LIKE %:artist% AND s.title LIKE %:title%")
    List<Song> findByArtistAndTitle(@Param("artist") String artist, @Param("title") String title);

    // 전체 텍스트 검색 (가수 OR 제목)
    @Query("SELECT s FROM Song s WHERE s.artist LIKE %:query% OR s.title LIKE %:query%")
    List<Song> searchByQuery(@Param("query") String query);
}
