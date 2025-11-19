interface VisualizationHeaderProps {
  title: string;
  artist: string;
  bpm: number;
  duration: number;
}

const VisualizationHeader = ({
  title,
  artist,
  bpm,
  duration
}: VisualizationHeaderProps) => {
  const formatDuration = (seconds: number): string => {
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return `${min}:${sec.toString().padStart(2, '0')}`;
  };

  return (
    <div className="viz-header">
      <h1>곡 시각화 도구</h1>
      <div className="viz-song-info">
        <div className="viz-song-title">{title} - {artist}</div>
        <div className="viz-song-meta">
          <span>BPM: {bpm.toFixed(2)}</span>
          <span>길이: {formatDuration(duration)}</span>
        </div>
      </div>
    </div>
  );
};

export default VisualizationHeader;
