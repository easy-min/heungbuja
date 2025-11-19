import type { Beat } from '../../types/visualization';

interface MetronomeVisualizerProps {
  currentBeat: Beat | null;
  beats: Beat[];
}

const MetronomeVisualizer = ({ currentBeat, beats }: MetronomeVisualizerProps) => {
  // 16비트 그리드 생성 (4마디 x 4비트) - 고정된 16개 슬롯에 비트들이 흘러감
  const renderBeats = () => {
    const beatElements = [];

    // 현재 비트의 인덱스 찾기
    const currentBeatIndex = currentBeat ? currentBeat.i : 0;

    // 현재 비트부터 시작해서 16개 비트 표시
    const startIndex = currentBeatIndex;
    const endIndex = Math.min(beats.length, startIndex + 16);

    // 실제 표시할 비트들
    const displayBeats = beats.slice(startIndex, endIndex);

    // 16칸을 채우기
    for (let i = 0; i < 16; i++) {
      const beat = displayBeats[i];
      const isActive = beat && currentBeat && beat.i === currentBeat.i;

      // 마디 시작점 (beat.beat === 1)
      const isBarStart = beat ? (beat.beat === 1) : false;

      beatElements.push(
        <div
          key={beat ? beat.i : `empty-${i}`}
          className={`viz-beat ${isActive ? 'active' : ''} ${isBarStart ? 'bar-start' : ''}`}
        >
          <div className="viz-beat-number">{beat ? beat.i + 1 : '-'}</div>
          <div className="viz-beat-bar">Bar {beat ? beat.bar : '-'}</div>
        </div>
      );
    }

    return beatElements;
  };

  return (
    <div className="viz-metronome">
      <h3 className="viz-metronome-title">메트로놈 (16 Beats)</h3>
      <div className="viz-metronome-grid">
        {renderBeats()}
      </div>
    </div>
  );
};

export default MetronomeVisualizer;
