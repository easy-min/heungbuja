import { forwardRef } from 'react';
import type { LyricLine, Action } from '../../types/visualization';
import ActionChip from './ActionChip';

interface LyricSectionProps {
  lyric: LyricLine;
  actions: Action[];
  isActive: boolean;
  currentAction: Action | null;
}

const LyricSection = forwardRef<HTMLDivElement, LyricSectionProps>(
  ({ lyric, actions, isActive, currentAction }, ref) => {
    // 시간 포맷팅
    const formatTime = (seconds: number): string => {
      const min = Math.floor(seconds / 60);
      const sec = Math.floor(seconds % 60);
      const ms = Math.floor((seconds % 1) * 10);
      return `${min}:${sec.toString().padStart(2, '0')}.${ms}`;
    };

    // 동작 개수와 박자 계산
    const actionCount = actions.length;
    const beats = lyric.ebeat - lyric.sbeat;

    return (
      <div ref={ref} className={`viz-lyric-section ${isActive ? 'active' : ''}`}>
        {/* 헤더 */}
        <div className="viz-lyric-section-header">
          <div className="viz-lyric-section-time">●{formatTime(lyric.start)}s</div>
          <div className="viz-lyric-section-text">{lyric.text}</div>
          <div className="viz-lyric-section-meta">
            ({beats}박자, 동작 {actionCount}개)
          </div>
        </div>

        {/* 동작 칩들 */}
        {actions.length > 0 && (
          <div className="viz-lyric-section-actions">
            {actions.map((action, idx) => (
              <ActionChip
                key={`${action.time}-${idx}`}
                action={action}
                index={idx}
                isActive={currentAction !== null && currentAction.time === action.time && currentAction.actionCode === action.actionCode}
              />
            ))}
          </div>
        )}
      </div>
    );
  }
);

LyricSection.displayName = 'LyricSection';

export default LyricSection;
