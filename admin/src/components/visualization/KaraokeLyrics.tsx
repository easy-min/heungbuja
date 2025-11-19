import type { LyricLine } from '../../types/visualization';

interface KaraokeLyricsProps {
  currentLyric: LyricLine | null;
  nextLyric: LyricLine | null;
  currentTime: number;
}

const KaraokeLyrics = ({
  currentLyric,
  nextLyric,
  currentTime
}: KaraokeLyricsProps) => {
  // 현재 가사의 진행률 계산
  const calculateProgress = (): number => {
    if (!currentLyric) return 0;

    const duration = currentLyric.end - currentLyric.start;
    const elapsed = currentTime - currentLyric.start;
    const progress = (elapsed / duration) * 100;

    return Math.max(0, Math.min(100, progress));
  };

  const progress = calculateProgress();

  return (
    <div className="viz-lyrics-karaoke">
      <div className="viz-current-lyric">
        {currentLyric ? currentLyric.text : '가사를 기다리는 중...'}
      </div>
      <div className="viz-next-lyric">
        {nextLyric ? `다음: ${nextLyric.text}` : currentLyric ? '마지막 가사' : '-'}
      </div>
      <div className="viz-lyric-progress">
        <div className="viz-lyric-progress-bar" style={{ width: `${progress}%` }} />
      </div>
    </div>
  );
};

export default KaraokeLyrics;
