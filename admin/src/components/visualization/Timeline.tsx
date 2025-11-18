import { useEffect, useRef } from 'react';
import type { LyricLine, Action } from '../../types/visualization';
import LyricSection from './LyricSection';

interface TimelineProps {
  lyrics: LyricLine[];
  actions: Action[];
  currentTime: number;
  currentAction: Action | null;
}

interface LyricGroup {
  lyric: LyricLine;
  actions: Action[];
}

const Timeline = ({ lyrics, actions, currentTime, currentAction }: TimelineProps) => {
  const currentItemRef = useRef<HTMLDivElement>(null);

  // 가사별로 동작 그룹화
  const groupActionsByLyric = (): LyricGroup[] => {
    const groups: LyricGroup[] = [];

    lyrics.forEach((lyric) => {
      // 이 가사의 시간 범위 내에 있는 동작들 필터링
      const lyricActions = actions.filter(
        (action) => action.time >= lyric.start && action.time < lyric.end
      );

      groups.push({
        lyric,
        actions: lyricActions,
      });
    });

    return groups;
  };

  const lyricGroups = groupActionsByLyric();

  // 현재 아이템으로 자동 스크롤
  useEffect(() => {
    if (currentItemRef.current) {
      currentItemRef.current.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      });
    }
  }, [currentTime]);

  // 타임라인이 비어있을 때
  if (lyricGroups.length === 0) {
    return (
      <div className="viz-timeline">
        <h3 className="viz-timeline-title">타임라인</h3>
        <div className="viz-timeline-empty">
          <p>가사 또는 동작 정보가 없습니다.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="viz-timeline">
      <h3 className="viz-timeline-title">타임라인</h3>
      <div className="viz-timeline-list">
        {lyricGroups.map((group, idx) => {
          // 현재 가사 구간인지 판단
          const isActive =
            currentTime >= group.lyric.start &&
            currentTime < group.lyric.end;

          return (
            <LyricSection
              key={`lyric-${idx}`}
              ref={isActive ? currentItemRef : null}
              lyric={group.lyric}
              actions={group.actions}
              isActive={isActive}
              currentAction={currentAction}
            />
          );
        })}
      </div>
    </div>
  );
};

export default Timeline;
