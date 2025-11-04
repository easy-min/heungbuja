import { useState, useRef, useCallback, useEffect } from 'react';
import { type BarGroup, type SongData, type Frame } from '@/types';
import { calculateBarGroups } from '@/utils';
import { GAME_CONFIG } from '@/utils/constants';

interface UseMusicMonitorProps {
  audioRef: React.RefObject<HTMLAudioElement>;
  onSegmentStart?: (segmentIndex: number) => void;
  onSegmentEnd?: (segmentIndex: number, frames: Frame[]) => void;
  onAllComplete?: () => void;
}

interface UseMusicMonitorReturn {
  barGroups: BarGroup[];
  currentSegmentIndex: number;
  isMonitoring: boolean;
  loadSongData: (jsonPath: string) => Promise<void>;
  startMonitoring: () => void;
  stopMonitoring: () => void;
}

export const useMusicMonitor = ({
  audioRef,
  onSegmentStart,
  onSegmentEnd,
  onAllComplete,
}: UseMusicMonitorProps): UseMusicMonitorReturn => {
  const [barGroups, setBarGroups] = useState<BarGroup[]>([]);
  const [currentSegmentIndex, setCurrentSegmentIndex] = useState(0);
  const [isMonitoring, setIsMonitoring] = useState(false);
  
  const animationFrameIdRef = useRef<number | null>(null);
  const hasStartedRef = useRef<boolean>(false);

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

      // 세그먼트 시간 계산
      const groups = calculateBarGroups(data.beats, data.sections || []);
      setBarGroups(groups);
      
      console.log('✅ 세그먼트 계산 완료:', groups);
    } catch (err) {
      console.error('❌ JSON 로드 실패:', err);
      throw err;
    }
  }, []);

  /**
   * 모니터링 시작
   */
  const startMonitoring = useCallback((): void => {
    if (!audioRef.current || barGroups.length === 0) {
      console.warn('⚠️  모니터링 시작 실패: audio 또는 barGroups 없음');
      return;
    }

    setIsMonitoring(true);
    setCurrentSegmentIndex(0);
    hasStartedRef.current = false;
    
    console.log('👀 음악 모니터링 시작');

    /**
     * requestAnimationFrame 기반 타이밍 체크
     */
    const checkTiming = () => {
      if (!isMonitoring || !audioRef.current) return;

      const currentTime = audioRef.current.currentTime;
      const group = barGroups[currentSegmentIndex];

      if (!group) {
        // 모든 세그먼트 완료
        console.log('🎉 모든 세그먼트 완료');
        stopMonitoring();
        onAllComplete?.();
        return;
      }

      // 세그먼트 시작 감지
      if (
        !hasStartedRef.current &&
        currentTime >= group.startTime - GAME_CONFIG.EPS &&
        currentTime < group.endTime - GAME_CONFIG.EPS
      ) {
        hasStartedRef.current = true;
        console.log(`▶️  세그먼트 ${group.segmentIndex} 시작 (${currentTime.toFixed(2)}s)`);
        onSegmentStart?.(currentSegmentIndex);
      }

      // 세그먼트 종료 감지
      if (hasStartedRef.current && currentTime >= group.endTime - GAME_CONFIG.EPS) {
        hasStartedRef.current = false;
        console.log(`⏹ 세그먼트 ${group.segmentIndex} 종료 (${currentTime.toFixed(2)}s)`);
        onSegmentEnd?.(currentSegmentIndex, []);
        
        // 다음 세그먼트로 이동
        setCurrentSegmentIndex((prev) => prev + 1);
      }

      animationFrameIdRef.current = requestAnimationFrame(checkTiming);
    };

    animationFrameIdRef.current = requestAnimationFrame(checkTiming);
  }, [audioRef, barGroups, currentSegmentIndex, isMonitoring, onSegmentStart, onSegmentEnd, onAllComplete]);

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
   * 컴포넌트 언마운트 시 정리
   */
  useEffect(() => {
    return () => {
      stopMonitoring();
    };
  }, [stopMonitoring]);

  return {
    barGroups,
    currentSegmentIndex,
    isMonitoring,
    loadSongData,
    startMonitoring,
    stopMonitoring,
  };
};