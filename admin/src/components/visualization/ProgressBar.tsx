import type { Section, Beat } from '../../types/visualization';

interface ProgressBarProps {
  currentTime: number;
  duration: number;
  sections: Section[];
  beats: Beat[];
}

const ProgressBar = ({
  currentTime,
  duration,
  sections,
  beats
}: ProgressBarProps) => {
  const progress = duration > 0 ? (currentTime / duration) * 100 : 0;

  const formatTime = (seconds: number): string => {
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return `${min}:${sec.toString().padStart(2, '0')}`;
  };

  return (
    <div className="viz-progress-container">
      <div className="viz-progress-wrapper">
        <div className="viz-progress-bar" style={{ width: `${progress}%` }} />
        <div className="viz-section-markers">
          {sections.map((section, idx) => {
            const startBeat = beats.find((b) => b.i === section.startBeat);
            const startTime = startBeat?.t || 0;

            return (
              <div key={idx} className="viz-section-marker">
                {section.label}
                <br />
                {formatTime(startTime)}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default ProgressBar;
