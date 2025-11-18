import { useEffect, useState } from 'react';
import './SongListPage.css';
import VoiceButton from '@/components/VoiceButton';
import SongListItem from '@/components/SongListItem';
import type { Song } from '@/types/song';

function SongListPage() {
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // TODO: 실제 API로 교체
    async function fetchSongs() {
      setLoading(true);
      try {
        // 예시 더미 데이터
        const data: Song[] = [
          { id: 1, rank: 1, title: '동반자', artist: '태진아' },
          { id: 2, rank: 2, title: '어머나', artist: '장윤정' },
          { id: 3, rank: 3, title: '당돌한 여자', artist: '서주경' },
          { id: 4, rank: 4, title: '샤방샤방', artist: '박현빈' },
          { id: 5, rank: 5, title: '유행가', artist: '송대관' },
        ];
        setSongs(data);
      } finally {
        setLoading(false);
      }
    }

    void fetchSongs();
  }, []);

  return (
    <>
      <div className="song-list-page">
        <div className="song-list__date-title">
          <p>2025년 10월 인기차트</p>
        </div>

        <div className="song-list__container">
          {loading && <div className="song-list__loading">불러오는 중…</div>}
          {!loading && songs.map(song => (
            <SongListItem key={song.id} song={song} />
          ))}
        </div>
      </div>
      <VoiceButton />
    </>
  );
}

export default SongListPage;