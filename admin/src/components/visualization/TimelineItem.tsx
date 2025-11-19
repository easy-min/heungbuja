import { forwardRef } from 'react';
import type { TimelineStatus, ActionIconMap } from '../../types/visualization';

interface TimelineItemProps {
  type: 'lyric' | 'action';
  text: string;
  time: number;
  status: TimelineStatus;
  actionCode?: number;
}

const TimelineItem = forwardRef<HTMLDivElement, TimelineItemProps>(
  ({ type, text, time, status, actionCode }, ref) => {
  // 시간 포맷팅
  const formatTime = (seconds: number): string => {
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return `${min}:${sec.toString().padStart(2, '0')}`;
  };

  // 동작 이모지 매핑
  const actionIcons: ActionIconMap = {
    0: '👏',      // 손 박수
    1: '👐',      // 팔 치기
    2: '🍑',      // 엉덩이 박수
    3: '🙆‍♀️',  // 팔 뻗기
    4: '🤸',      // 기우뚱
    5: '🚪',      // 비상구
    6: '🙋',      // 겨드랑이박수
    7: '💃',      // 기타
  };

  const icon = type === 'action' && actionCode !== undefined
    ? actionIcons[actionCode] || '💃'
    : '🎵';

  return (
    <div ref={ref} className={`viz-timeline-item ${type} ${status}`}>
      <div className="viz-timeline-icon">{icon}</div>
      <div className="viz-timeline-content">
        <div className="viz-timeline-text">{text}</div>
        <div className="viz-timeline-time">{formatTime(time)}</div>
      </div>
    </div>
  );
});

TimelineItem.displayName = 'TimelineItem';

export default TimelineItem;
