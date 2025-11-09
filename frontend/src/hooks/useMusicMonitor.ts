import { useState, useRef, useCallback, useEffect } from 'react';
import { type BarGroup, type SongData, type Frame, type Beat, type Section } from '@/types';
import type { SongTimeline } from '@/types/song';
import { calculateBarGroups } from '@/utils';
import { GAME_CONFIG } from '@/utils/constants';

type LoadFromGameStartArgs = {
  bpm: number;
  duration: number;
  timeline: SongTimeline;
  beatsPerBar?: number;
};

interface SectionTime {
  label: 'intro' | 'break' | 'verse1' | 'verse2';
  startTime: number;
  endTime: number;
}

interface UseMusicMonitorProps {
  audioRef: React.RefObject<HTMLAudioElement | null>;
  onSegmentStart?: (segmentIndex: number) => void;
  onSegmentEnd?: (segmentIndex: number, frames: Frame[]) => void;
  onAllComplete?: () => void;
  onSectionEnter?: (label: SectionTime['label']) => void;
}

interface UseMusicMonitorReturn {
  barGroups: BarGroup[];
  currentSegmentIndex: number;
  isMonitoring: boolean;
  songBpm: number;
  sectionTimes: SectionTime[];
  loadSongData: (jsonPath: string) => Promise<void>;
  startMonitoring: () => void;
  stopMonitoring: () => void;
  loadFromGameStart: (args: {
    bpm: number;
    duration: number;
    timeline: SongTimeline;
    beatsPerBar?: number;
  }) => Promise<void>;
}

function buildSectionTimes(beats: Beat[], sections: Section[]): SectionTime[] {
  const firstBeatOfBar = new Map<number, number>();
  const lastBeatOfBar  = new Map<number, number>();

  for (const b of beats) {
    if (!firstBeatOfBar.has(b.bar)) firstBeatOfBar.set(b.bar, b.t);
    lastBeatOfBar.set(b.bar, b.t); // 마지막 beat 시간이 남음
  }

  return (sections || []).map(s => {
    const startTime = firstBeatOfBar.get(s.startBar) ?? 0;
    const endTimeRaw = lastBeatOfBar.get(s.endBar) ?? startTime;
    return {
      label: s.label as SectionTime['label'],
      startTime,
      endTime: endTimeRaw,
    };
  }).sort((a, b) => a.startTime - b.startTime);
}

export const useMusicMonitor = ({
  audioRef,
  onSegmentStart,
  onSegmentEnd,
  onAllComplete,
  onSectionEnter,
}: UseMusicMonitorProps): UseMusicMonitorReturn => {
  const [barGroups, setBarGroups] = useState<BarGroup[]>([]);
  const [currentSegmentIndex, setCurrentSegmentIndex] = useState(0);
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [songBpm, setSongBpm] = useState<number>(100);
  const [sectionTimes, setSectionTimes] = useState<SectionTime[]>([]);

  const animationFrameIdRef = useRef<number | null>(null);
  const hasStartedRef = useRef<boolean>(false);
  const currentSegmentIndexRef = useRef<number>(0);
  const currentSectionIdxRef = useRef<number>(-1);

  const sectionTimesRef = useRef<SectionTime[]>([]);

  // 현재 시각으로 섹션 감지
  const detectSectionAt = (t: number) => {
    const secs = sectionTimesRef.current;
    if (!secs.length) return;

    const eps = GAME_CONFIG.EPS;
    const curIdx = currentSectionIdxRef.current;

    // 현재 섹션 유지 중이면 아무 것도 안 함
    if (
      curIdx >= 0 &&
      curIdx < secs.length &&
      t >= secs[curIdx].startTime - eps &&
      t <  secs[curIdx].endTime   - eps
    ) {
      return;
    }

    // 재탐색
    const found = secs.findIndex(
      (s) => t >= s.startTime - eps && t < s.endTime - eps
    );
    if (found !== -1 && found !== currentSectionIdxRef.current) {
      currentSectionIdxRef.current = found;
      onSectionEnter?.(secs[found].label);
    }
  };

  useEffect(() => {
    sectionTimesRef.current = sectionTimes;
  }, [sectionTimes]);

  /**
   * JSON 데이터 로드 및 세그먼트 계산
   */
  const loadSongData = useCallback(async (jsonPath: string): Promise<void> => {
    try {
      console.log('📥 JSON 데이터 로드 중...', jsonPath);
      
      const response = await fetch(jsonPath);
      const data: SongData = await response.json();

      if (!data.beats || data.beats.length === 0) {
        throw new Error('beats 데이터가 없습니다');
      }

      // 비트 계산
      const bpm = Number((data as any)?.tempoMap?.[0]?.bpm);
      if (!Number.isFinite(bpm)) {
        console.warn('⚠️ tempoMap[0].bpm 없음. 기본 120 사용');
        setSongBpm(120);
      } else {
        setSongBpm(bpm);
      }

      // 세그먼트 시간 계산
      const groups = calculateBarGroups(data.beats, data.sections || []);
      setBarGroups(groups);
      console.log('✅ 세그먼트 계산 완료:', groups);
      
      // 섹션 타임라인 계산 추가
      const secTimes = buildSectionTimes(data.beats, data.sections || []);
      setSectionTimes(secTimes);
      console.log('✅ 섹션 타임라인 계산 완료:', secTimes);
      
    } catch (err) {
      console.error('❌ JSON 로드 실패:', err);
      throw err;
    }
  }, []);

  /**
   * 모니터링 중지
   */
  const stopMonitoring = useCallback((): void => {
    setIsMonitoring(false);
    
    if (animationFrameIdRef.current !== null) {
      cancelAnimationFrame(animationFrameIdRef.current);
      animationFrameIdRef.current = null;
    }
    
    console.log('⏸ 음악 모니터링 중지');
  }, []);
  /**
   * 모니터링 시작
   */
  const startMonitoring = useCallback((): void => {
  // console.log('🟢 startMonitoring 호출됨');  // ✅ 추가
  // console.log('🔍 audioRef.current:', audioRef.current);  // ✅ 추가
  // console.log('🔍 barGroups.length:', barGroups.length);  // ✅ 추가

    if (!audioRef.current || barGroups.length === 0) {
      console.warn('⚠️  모니터링 시작 실패: audio 또는 barGroups 없음');
      return;
    }

    setIsMonitoring(true);
    setCurrentSegmentIndex(0);
    currentSegmentIndexRef.current = 0;
    hasStartedRef.current = false;
    currentSectionIdxRef.current = -1;

    console.log('👀 음악 모니터링 시작');
      console.log('🔍 첫 세그먼트:', barGroups[0]);  // ✅ 추가

    /**
     * requestAnimationFrame 기반 타이밍 체크
     */
    const checkTiming = () => {      
      if (animationFrameIdRef.current === null) return;
      const au = audioRef.current;
      if (!au) return;

      const currentTime = au.currentTime;
      const group = barGroups[currentSegmentIndexRef.current];
      // console.log(`⏰ currentTime: ${currentTime.toFixed(2)}, segmentIndex: ${currentSegmentIndexRef.current}, group:`, group);  // ✅ 추가


      // --- (1) 섹션 감지: 루프 안에서 매 프레임 확인) ---
      detectSectionAt(currentTime);

      // --- (2) 세그먼트 감지: 기존 그대로 ---
      if (!group) {
        stopMonitoring();
        onAllComplete?.();
        return;
      }
      if (
        !hasStartedRef.current &&
        currentTime >= group.startTime - GAME_CONFIG.EPS &&
        currentTime <  group.endTime   - GAME_CONFIG.EPS
      ) {
        hasStartedRef.current = true;
        onSegmentStart?.(currentSegmentIndexRef.current);
      }
      if (hasStartedRef.current && currentTime >= group.endTime - GAME_CONFIG.EPS) {
        hasStartedRef.current = false;
        onSegmentEnd?.(currentSegmentIndexRef.current, []);
        currentSegmentIndexRef.current += 1;
        setCurrentSegmentIndex(currentSegmentIndexRef.current);
      }

      animationFrameIdRef.current = requestAnimationFrame(checkTiming);
    };

    animationFrameIdRef.current = requestAnimationFrame(checkTiming);
  }, [audioRef, barGroups, onSegmentStart, onSegmentEnd, onAllComplete, onSectionEnter, stopMonitoring]);

  // 오디오의 timeupdate/seeked 때도 섹션 감지 (rAF가 잠깐 쉬어도 놓치지 않게)
  useEffect(() => {
    const au = audioRef.current;
    if (!au) return;

    const onTime = () => detectSectionAt(au.currentTime);
    const onSeek = () => detectSectionAt(au.currentTime);
    const onPlay = () => detectSectionAt(au.currentTime); // 시작 직후 한 번 보정

    au.addEventListener('timeupdate', onTime);
    au.addEventListener('seeked', onSeek);
    au.addEventListener('play', onPlay);

    return () => {
      au.removeEventListener('timeupdate', onTime);
      au.removeEventListener('seeked', onSeek);
      au.removeEventListener('play', onPlay);
    };
  }, [audioRef, onSectionEnter]);

  /**
   * 컴포넌트 언마운트 시 정리
   */
  useEffect(() => {
    return () => {
      stopMonitoring();
    };
  }, [stopMonitoring]);

  function buildSongDataFromTimeline({
    bpm, duration, timeline, beatsPerBar = 4,
  }: LoadFromGameStartArgs) {
    const beatLen = 60 / bpm;

    // beats 생성
    const beats: Beat[] = [];
    let t = 0, bar = 1, beatInBar = 1;
    while (t <= duration + 1e-3) {
      beats.push({ t: Number(t.toFixed(6)), bar, beat: beatInBar });
      beatInBar++;
      if (beatInBar > beatsPerBar) { beatInBar = 1; bar++; }
      t += beatLen;
    }

    // 첫박 기준 bar-시각 map
    const firstBeatOfBar = new Map<number, number>();
    for (const b of beats) {
      if (b.beat === 1 && !firstBeatOfBar.has(b.bar)) firstBeatOfBar.set(b.bar, b.t);
    }
    const timeToNearestBar = (sec: number) => {
      let bestBar = 1, best = Infinity;
      for (const [br, bt] of firstBeatOfBar) {
        const d = Math.abs(bt - sec);
        if (d < best) { best = d; bestBar = br; }
      }
      return bestBar;
    };

    // sections 생성
    const pts = [
      { label: 'intro' as const, startTime: timeline.introStartTime },
      { label: 'break' as const, startTime: timeline.breakStartTime },
      { label: 'verse1' as const, startTime: timeline.verse1StartTime },
      { label: 'verse2' as const, startTime: timeline.verse2StartTime },
    ].sort((a, b) => a.startTime - b.startTime);

    const sections: Section[] = pts.map((s, i) => {
      const startBar = timeToNearestBar(s.startTime);
      const nextStart = pts[i + 1]?.startTime ?? duration;
      const endBar = Math.max(startBar, timeToNearestBar(nextStart) - 1);
      return { label: s.label, startBar, endBar };
    });

    // 기존 calculateBarGroups/sectionTimes 계산 경로에 맞춤
    return { beats, sections, tempoMap: [{ bpm }] } as SongData;
  }

  // (신규) 응답 데이터 기반 로더
  const loadFromGameStart = useCallback(async (args: LoadFromGameStartArgs) => {
    const obj = buildSongDataFromTimeline(args);
    // 기존 경로를 재사용하기 위해 Blob → ObjectURL 기법 사용
    const blob = new Blob([JSON.stringify(obj)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    try {
      await loadSongData(url);
    } finally {
      URL.revokeObjectURL(url);
    }
  }, [loadSongData]);

  return {
    barGroups,
    currentSegmentIndex,
    isMonitoring,
    songBpm,
    sectionTimes,
    loadSongData,
    startMonitoring,
    stopMonitoring,
    loadFromGameStart,
  };

};