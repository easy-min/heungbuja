import type { SongInfo } from '@/types/voiceCommand';
import './SongPage.css';
import VoiceButton from '@/components/VoiceButton';
import { useRef, useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useAudioStore } from '@/store/audioStore';

interface SongPageState {
  songInfo: SongInfo;
  autoPlay?: boolean;
}

// 테스트용 더미 데이터 (컴포넌트 외부로 이동)
const BASE_URL = import.meta.env.BASE_URL;
const dummySongInfo: SongInfo = {
  songId: 1,
  title: '테스트 노래 - 당돌한 여자',
  artist: '테스트 가수 - 서주경',
  mediaId: 100,
  audioUrl: `${BASE_URL}당돌한여자.mp3`,
  mode: 'LISTENING'
};



function SongPage() {
  const location = useLocation();
  const state = location.state as SongPageState | null;

  // 전달받은 songInfo 또는 더미 데이터 사용
  const songInfo = state?.songInfo || dummySongInfo;
  const autoPlay = state?.autoPlay || false;

  const audioRef = useRef<HTMLAudioElement>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const { setAudioRef, setIsPlaying: setGlobalPlaying } = useAudioStore();

  // audioRef를 store에 등록
  useEffect(() => {
    if (audioRef.current) {
      setAudioRef(audioRef.current);
    }
    return () => {
      setAudioRef(null);
    };
  }, [setAudioRef]);

  // isPlaying 상태를 store와 동기화
  useEffect(() => {
    setGlobalPlaying(isPlaying);
  }, [isPlaying, setGlobalPlaying]);

  return (
    <div className="container">
      {/* 블러 그라디언트 배경 */}
      <span className='song-title'>{songInfo.title}</span>
      <span className='song-artist'>{songInfo.artist}</span>

      {/* LP판 턴테이블 */}
      <div className={`lp-container ${isPlaying ? 'playing' : 'stopped'}`}>
        <div className="lp-disc"></div>
        <div className="tonearm-base"></div>
        <div className="tonearm"></div>
      </div>

      {/* 오디오 플레이어 */}
      <audio
        ref={audioRef}
        src={songInfo.audioUrl}
        autoPlay={autoPlay}
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onEnded={() => setIsPlaying(false)}
      />

      <VoiceButton/>
    </div>
  );
}

export default SongPage;
