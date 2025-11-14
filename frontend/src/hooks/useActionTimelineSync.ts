import { useEffect, useState } from 'react';
import type { actionLine, SongTimeline } from '@/types/game';

export function useActionTimelineSync({
  audioRef,
  currentSectionRef,
  verse1Timeline,
  verse2Timelines,
  sectionInfo,
  verse2Level = 'level2',   // 기본 level2
}: {
  audioRef: React.RefObject<HTMLAudioElement | null>;
  currentSectionRef: React.MutableRefObject<'intro' | 'break' | 'verse1' | 'verse2'>;
  verse1Timeline: actionLine[] | undefined;
  verse2Timelines: { level1: actionLine[]; level2: actionLine[]; level3: actionLine[] } | undefined;
  sectionInfo: SongTimeline | undefined;
  verse2Level?: 'level1' | 'level2' | 'level3';
}) {
  const [currentActionName, setCurrentActionName] = useState<string | null>(null);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !sectionInfo) return;

    const findCurrent = (t: number, timeline: actionLine[]) => {
      let cur: actionLine | null = null;
      for (let i = 0; i < timeline.length; i++) {
        const absT = timeline[i].time; // ← 절대 시간 그대로 사용
        if (absT <= t) cur = timeline[i];
        else break;
      }
      return cur;
    };

    const handle = () => {
      const t = audio.currentTime;
      const section = currentSectionRef.current;

      if (section === 'verse1' && verse1Timeline) {
        const act = findCurrent(t, verse1Timeline);
        setCurrentActionName(act?.actionName ?? null);
        return;
      }

      // console.log('[ENTER SECTION]', currentSectionRef)
      // console.log('[verse2Timeline level2]', verse2Timeline?.level2)
      // console.log('[verse2Timeline times]', verse2Timeline?.level2?.map(t => t.time))

      if (section === 'verse2' && verse2Timelines) {
        const timeline = verse2Timelines[verse2Level] ?? [];
        const act = findCurrent(t, timeline);
        setCurrentActionName(act?.actionName ?? null);
        return;
      }

      setCurrentActionName(null);
    };

    audio.addEventListener('timeupdate', handle);
    return () => audio.removeEventListener('timeupdate', handle);
  }, [verse1Timeline, verse2Timelines, sectionInfo, verse2Level]);

  return currentActionName;
}
