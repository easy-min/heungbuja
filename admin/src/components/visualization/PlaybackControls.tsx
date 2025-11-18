interface PlaybackControlsProps {
  isPlaying: boolean;
  onPlay: () => void;
  onPause: () => void;
  onStop: () => void;
}

const PlaybackControls = ({
  isPlaying,
  onPlay,
  onPause,
  onStop
}: PlaybackControlsProps) => {
  return (
    <div className="viz-controls">
      <button
        className="viz-btn viz-btn-primary"
        onClick={onPlay}
        disabled={isPlaying}
      >
        ▶ 재생
      </button>
      <button
        className="viz-btn viz-btn-secondary"
        onClick={onPause}
        disabled={!isPlaying}
      >
        ⏸ 일시정지
      </button>
      <button
        className="viz-btn viz-btn-secondary"
        onClick={onStop}
      >
        ⏹ 정지
      </button>
    </div>
  );
};

export default PlaybackControls;
