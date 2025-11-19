interface SectionDisplayProps {
  sectionName: string;
  currentTime: number;
  totalTime: number;
}

const SectionDisplay = ({
  sectionName,
  currentTime,
  totalTime
}: SectionDisplayProps) => {
  const formatTime = (seconds: number): string => {
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return `${min}:${sec.toString().padStart(2, '0')}`;
  };

  const sectionNames: { [key: string]: string } = {
    'intro': 'Intro',
    'verse1': 'Verse1 (1절 안무)',
    'break': 'Break (휴식)',
    'verse2': 'Verse2 (2절 안무)',
  };

  const displayName = sectionNames[sectionName.toLowerCase()] || sectionName;

  return (
    <div className="viz-current-section">
      <div className="viz-section-name">{displayName}</div>
      <div className="viz-time-display">
        <span>{formatTime(currentTime)}</span> / <span>{formatTime(totalTime)}</span>
      </div>
    </div>
  );
};

export default SectionDisplay;
