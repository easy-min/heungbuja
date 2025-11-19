import { useEffect, useRef } from 'react';
import { useVisualizationStore } from '../stores';

/**
 * 곡 시각화 재생 로직을 관리하는 커스텀 훅
 * - setInterval로 currentTime을 0.05초마다 업데이트
 * - 재생/일시정지/정지 기능 제공
 */
export const useVisualizationPlayer = () => {
  const intervalRef = useRef<number | null>(null);

  const {
    visualizationData,
    isPlaying,
    setIsPlaying,
    setCurrentTime,
    updatePlaybackPosition,
  } = useVisualizationStore();

  // 재생 시작
  const play = () => {
    if (!visualizationData) {
      console.warn('시각화 데이터가 없습니다.');
      return;
    }

    if (isPlaying) {
      console.warn('이미 재생 중입니다.');
      return;
    }

    setIsPlaying(true);

    // 0.05초(50ms)마다 currentTime 업데이트
    intervalRef.current = window.setInterval(() => {
      const state = useVisualizationStore.getState();
      const prevTime = state.currentTime;
      const newTime = prevTime + 0.05;

      // 곡이 끝나면 정지
      if (newTime >= visualizationData.duration) {
        stop();
        state.setCurrentTime(visualizationData.duration);
        state.updatePlaybackPosition(visualizationData.duration);
        return;
      }

      // 재생 위치 업데이트 (비트, 가사, 섹션, 동작 계산)
      state.setCurrentTime(newTime);
      state.updatePlaybackPosition(newTime);
    }, 50);
  };

  // 일시정지
  const pause = () => {
    if (!isPlaying) {
      console.warn('재생 중이 아닙니다.');
      return;
    }

    setIsPlaying(false);

    if (intervalRef.current !== null) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };

  // 정지 (currentTime을 0으로 리셋)
  const stop = () => {
    setIsPlaying(false);

    if (intervalRef.current !== null) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    // currentTime을 0으로 리셋하고 재생 위치 업데이트
    setCurrentTime(0);
    updatePlaybackPosition(0);
  };

  // 컴포넌트 언마운트 시 interval 정리
  useEffect(() => {
    return () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  // 시각화 데이터가 변경되면 재생 정지
  useEffect(() => {
    if (isPlaying) {
      stop();
    }
  }, [visualizationData]);

  return {
    play,
    pause,
    stop,
    isPlaying,
  };
};
