import { create } from 'zustand';
import type { SongInfo, SongTimeline, LyricLine } from '@/types/song';

export interface GameState {
  sessionId: string | null;
  songInfo: SongInfo | null;
  timeline: SongTimeline | null;
  lyrics: LyricLine[];
  videoUrls: Record<string, string>;
  setAll: (p: Partial<GameState>) => void;
  clear: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  sessionId: null,
  songInfo: null,
  timeline: null,
  lyrics: [],
  videoUrls: {},
  setAll: (p) => set(p),
  clear: () =>
    set({
      sessionId: null,
      songInfo: null,
      timeline: null,
      lyrics: [],
      videoUrls: {},
    }),
}));
