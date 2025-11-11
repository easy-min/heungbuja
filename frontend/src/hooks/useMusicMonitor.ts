import { useState, useRef, useCallback, useEffect } from 'react';
import type { SongTimeline } from '@/types/song';
import { GAME_CONFIG } from '@/utils/constants';

type LoadFromGameStartArgs = {
  bpm: number;
  duration: number;
  timeline: SongTimeline;
};

interface SectionTime {
  label: 'intro' | 'break' | 'verse1' | 'verse2';
  startTime: number;
  endTime: number;
}

interface UseMusicMonitorProps {
  audioRef: React.RefObject<HTMLAudioElement | null>;
  onSectionEnter?: (label: SectionTime['label']) => void;
}

// ---- helpers ----
function buildSectionTimesFromAnchors(duration: number, timeline: SongTimeline): SectionTime[] {
  const pts = [
    { label: 'intro' as const,  start: timeline.introStartTime },
    { label: 'verse1' as const, start: timeline.verse1StartTime },
    { label: 'break' as const,  start: timeline.breakStartTime },
    { label: 'verse2' as const, start: timeline.verse2StartTime },
  ]
    .filter(p => typeof p.start === 'number' && !isNaN(p.start))
    .sort((a, b) => a.start - b.start);

  return pts.map((p, i) => ({
    label: p.label,
    startTime: p.start,
    endTime: pts[i + 1]?.start ?? duration,
  }));
}

// ---- hook ----
export const useMusicMonitor = (props: UseMusicMonitorProps) => {
  const { audioRef, onSectionEnter } = props;
  const [sectionTimes, setSectionTimes] = useState<SectionTime[]>([]);

  const animationFrameIdRef = useRef<number | null>(null);
  const currentSectionIdxRef = useRef(-1);
  const sectionTimesRef = useRef<SectionTime[]>([]);

  const detectSectionAt = (t: number) => {
    const secs = sectionTimesRef.current;
    if (!secs.length) return;
    const eps = GAME_CONFIG.EPS;
    const curIdx = currentSectionIdxRef.current;

    if (curIdx >= 0 && curIdx < secs.length &&
        t >= secs[curIdx].startTime - eps &&
        t <  secs[curIdx].endTime   - eps) return;

    const found = secs.findIndex(s => t >= s.startTime - eps && t < s.endTime + eps);
    if (found !== -1 && found !== currentSectionIdxRef.current) {
      currentSectionIdxRef.current = found;
      // console.log('🎬 Section Entered:', secs[found].label, secs[found]);
      onSectionEnter?.(secs[found].label);
    }
  };

  useEffect(() => { sectionTimesRef.current = sectionTimes; }, [sectionTimes]);

  const stopMonitoring = useCallback(() => {
    if (animationFrameIdRef.current !== null) {
      cancelAnimationFrame(animationFrameIdRef.current);
      animationFrameIdRef.current = null;
    }
  }, []);

  const startMonitoring = useCallback(() => {
    if (!audioRef.current) {
      console.warn('⚠️ audioRef 없음');
      return;
    }
    currentSectionIdxRef.current = -1;

    const tick = () => {
      if (animationFrameIdRef.current === null) return;
      const au = audioRef.current;
      if (!au) return;

      detectSectionAt(au.currentTime);

      // 세그먼트 기능이 필요하면 여기에서 추가
      animationFrameIdRef.current = requestAnimationFrame(tick);
    };
    animationFrameIdRef.current = requestAnimationFrame(tick);
  }, [audioRef]);

  useEffect(() => {
    const au = audioRef.current;
    if (!au) return;
    const onTime = () => detectSectionAt(au.currentTime);
    au.addEventListener('timeupdate', onTime);
    au.addEventListener('seeked', onTime);
    au.addEventListener('play', onTime);
    return () => {
      au.removeEventListener('timeupdate', onTime);
      au.removeEventListener('seeked', onTime);
      au.removeEventListener('play', onTime);
    };
  }, [audioRef, onSectionEnter]);

  useEffect(() => () => stopMonitoring(), [stopMonitoring]);

  const loadFromGameStart = useCallback(async ({duration, timeline }: LoadFromGameStartArgs) => {
    setSectionTimes(buildSectionTimesFromAnchors(duration, timeline));
  }, []);

  return {
    startMonitoring,
    stopMonitoring,
    loadFromGameStart,
  };
};