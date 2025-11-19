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
  const listRef = useRef<HTMLDivElement | null>(null);
  const currentItemRef = useRef<HTMLDivElement | null>(null);

  // 가사별로 동작 그룹화
  const lyricGroups: LyricGroup[] = lyrics.map((lyric) => ({
    lyric,
    actions: actions.filter(
      (action) => action.time >= lyric.start && action.time < lyric.end
    ),
  }));

  // 현재 활성화된 가사 index 계산
  const activeIndex = lyricGroups.findIndex(
    (group) => currentTime >= group.lyric.start && currentTime < group.lyric.end
  );

  // 활성 가사로만 스크롤 (타임라인 컨테이너 안에서만)
  useEffect(() => {
    if (activeIndex === -1) return;

    const container = listRef.current;
    const item = currentItemRef.current;
    if (!container || !item) return;

    const containerRect = container.getBoundingClientRect();
    const itemRect = item.getBoundingClientRect();

    const containerHeight = containerRect.height;
    const itemHeight = itemRect.height;

    // 아이템의 "컨테이너 내부 기준" 위치
    const offsetWithinContainer = itemRect.top - containerRect.top;

    // 현재 scrollTop을 기준으로 중앙에 맞도록 목표 값 계산
    const targetScrollTop =
      container.scrollTop + offsetWithinContainer - containerHeight / 2 + itemHeight / 2;

    container.scrollTo({
      top: targetScrollTop,
      behavior: 'smooth',
    });
  }, [activeIndex]); // currentTime 대신 activeIndex 기준으로만 이동

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
      <div className="viz-timeline-list" ref={listRef}>
        {lyricGroups.map((group, idx) => {
          const isActive = idx === activeIndex;

          return (
            <LyricSection
              key={`lyric-${idx}`}
              // 활성 섹션에만 ref 연결
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
