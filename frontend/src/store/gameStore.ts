import { create } from 'zustand';
import type { SongInfo, SongTimeline, LyricLine, SegmentRange } from '@/types/song';

export interface GameState {
  sessionId: string | null;
  songInfo: SongInfo | null;
  timeline: SongTimeline | null;
  segments: { verse1: SegmentRange; verse2: SegmentRange } | null;
  lyrics: LyricLine[];
  videoUrls: Record<string, string>;
  setAll: (p: Partial<GameState>) => void;
  clear: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  sessionId: null,
  songInfo: null,
  timeline: null,
  segments: null,
  lyrics: [],
  videoUrls: {},
  setAll: (p) => set(p),
  clear: () =>
    set({
      sessionId: null,
      songInfo: null,
      timeline: null,
      segments: null,
      lyrics: [],
      videoUrls: {},
    }),
}));
