interface LevelSelectorProps {
  selectedLevel: 'level1' | 'level2' | 'level3';
  onLevelChange: (level: 'level1' | 'level2' | 'level3') => void;
}

const LevelSelector = ({ selectedLevel, onLevelChange }: LevelSelectorProps) => {
  const levels: Array<'level1' | 'level2' | 'level3'> = ['level1', 'level2', 'level3'];

  const levelLabels = {
    level1: 'Level 1',
    level2: 'Level 2',
    level3: 'Level 3',
  };

  return (
    <div className="viz-level-selector">
      {levels.map((level) => (
        <button
          key={level}
          className={`viz-level-btn ${selectedLevel === level ? 'active' : ''}`}
          onClick={() => onLevelChange(level)}
        >
          {levelLabels[level]}
        </button>
      ))}
    </div>
  );
};

export default LevelSelector;
