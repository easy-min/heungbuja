import type { GameStartResponse } from '@/types/song';
import api from './index';
import { mockGameStart } from '@/mocks/gameStart.mock';

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

export const gameStartApi = (songId: number) => {
  if (USE_MOCK) {
    return mockGameStart(songId);
  }
  return api.post<GameStartResponse>('/game/start', { songId }, true);
}