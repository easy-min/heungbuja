import { useEffect, useRef, useState, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { SongInfo } from '@/types/voiceCommand';
import './SongPage.css';

interface SongPageState {
  songInfo: SongInfo;
  autoPlay?: boolean;
}

// 테스트용 더미 데이터 (컴포넌트 외부로 이동)
const dummySongInfo: SongInfo = {
  songId: 1,
  title: '테스트 노래 - 당돌한 여자',
  artist: '테스트 가수 - 서주경',
  mediaId: 100,
  audioUrl: '/당돌한여자.mp3',
  mode: 'LISTENING'
};

function SongPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const state = location.state as SongPageState | null;
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  // state 없으면 테스트 데이터 사용 (useMemo로 메모이제이션)
  const songInfo = useMemo(() =>
    state?.songInfo || dummySongInfo,
    [state?.songInfo]
  );

  const autoPlay = state?.autoPlay !== false;

  // state 없으면 홈으로 리다이렉트 (프로덕션에서는 활성화)
  // useEffect(() => {
  //   if (!state || !state.songInfo) {
  //     console.warn('노래 정보가 없습니다. 홈으로 이동합니다.');
  //     navigate('/');
  //   }
  // }, [state, navigate]);

  // 오디오 로드 및 자동재생
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    // 오디오 이벤트 핸들러
    const handleLoadedMetadata = () => {
      setDuration(audio.duration);
    };

    const handleTimeUpdate = () => {
      setCurrentTime(audio.currentTime);
    };

    const handlePlay = () => {
      setIsPlaying(true);
    };

    const handlePause = () => {
      setIsPlaying(false);
    };

    const handleEnded = () => {
      setIsPlaying(false);
    };

    audio.addEventListener('loadedmetadata', handleLoadedMetadata);
    audio.addEventListener('timeupdate', handleTimeUpdate);
    audio.addEventListener('play', handlePlay);
    audio.addEventListener('pause', handlePause);
    audio.addEventListener('ended', handleEnded);

    // 자동재생
    if (autoPlay) {
      audio.play().catch((err) => {
        console.error('자동재생 실패:', err);
      });
    }

    return () => {
      audio.removeEventListener('loadedmetadata', handleLoadedMetadata);
      audio.removeEventListener('timeupdate', handleTimeUpdate);
      audio.removeEventListener('play', handlePlay);
      audio.removeEventListener('pause', handlePause);
      audio.removeEventListener('ended', handleEnded);
      audio.pause();
    };
  }, [songInfo.audioUrl, autoPlay]); // audioUrl이 바뀔 때만 재실행


  return (
    <div className="container">
      {/* 블러 그라디언트 배경 */}
      <div className="gradient-blur"></div>

      {/* 중앙 흰색 원 */}
      <div className="white-circle">
        {/* 음표 아이콘 또는 앨범 커버 */}
        <div className="song-info">
          <h2 className="song-title">{songInfo.title}</h2>
          <p className="song-artist">{songInfo.artist}</p>
        </div>

        {/* 재생 상태 표시 */}
        {isPlaying && (
          <div className="playing-indicator">
            ♪ 재생 중
          </div>
        )}
      </div>

      {/* 오디오 플레이어 */}
      <audio
        ref={audioRef}
        src={songInfo.audioUrl}
        controls
        className="audio-player"
      />

    </div>
  );
}

export default SongPage;