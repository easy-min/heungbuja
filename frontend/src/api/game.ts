import type { GameEndResponse } from '@/types/game';
import api from './index';
import { useGameStore } from '@/store/gameStore';
import { mockGameStart } from '@/mocks/gameStart.mock';

// const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

export const gameStartApi = () => {
  // if (USE_MOCK) {
    return mockGameStart();
  // }
  // return api.post<GameStartResponse>('/game/start', { songId }, true);
}

export const gameEndApi = () => {
  const { sessionId } = useGameStore.getState();

  if (!sessionId) {
    throw new Error('세션 ID가 없습니다. 게임 시작 정보가 제대로 설정되지 않았습니다.');
  }

  const path = `/game/end?sessionId=${encodeURIComponent(sessionId)}`;
  return api.post<GameEndResponse, undefined>(
    path,
    undefined,
    false,
  );
};