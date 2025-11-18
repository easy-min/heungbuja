import LevelSelector from './LevelSelector';

interface PlaybackControlsProps {
  isPlaying: boolean;
  onPlay: () => void;
  onPause: () => void;
  onStop: () => void;
  selectedLevel: 'level1' | 'level2' | 'level3';
  onLevelChange: (level: 'level1' | 'level2' | 'level3') => void;
}

const PlaybackControls = ({
  isPlaying,
  onPlay,
  onPause,
  onStop,
  selectedLevel,
  onLevelChange,
}: PlaybackControlsProps) => {
  return (
    <div className="viz-controls">
      <div className="viz-controls-playback">
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

      <LevelSelector
        selectedLevel={selectedLevel}
        onLevelChange={onLevelChange}
      />
    </div>
  );
};

export default PlaybackControls;
