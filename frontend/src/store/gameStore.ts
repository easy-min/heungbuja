import { create } from 'zustand';
import type {
  GameStartResponse,
  LyricLine,
  SegmentRange,
  actionLine,
} from '@/types/game';

export interface GameState {
  // 식별/기본 정보
  sessionId: string | null;
  songId: number | null;
  songTitle: string | null;
  songArtist: string | null;

  // 미디어/메타
  audioUrl: string | null;
  videoUrls: Record<string, string>;
  bpm: number | null;
  duration: number | null;

  // 섹션/세그먼트
  sectionInfo: Record<string, number>;
  segmentInfo: {
    verse1cam: SegmentRange | null;
    verse2cam: SegmentRange | null;
  };

  // 가사/액션 타임라인
  lyrics: LyricLine[]; // (= API의 lyricsInfo)
  verse1Timeline: actionLine[];
  verse2Timeline: {
    level1: actionLine[];
    level2: actionLine[];
    level3: actionLine[];
  };

  // 업데이트 유틸
  setAll: (p: Partial<GameState>) => void;
  setFromApi: (resp: GameStartResponse) => void;
  clear: () => void;
}


export const useGameStore = create<GameState>((set) => ({
  // 기본값
  sessionId: null,
  songId: null,
  songTitle: null,
  songArtist: null,

  audioUrl: null,
  videoUrls: {},
  bpm: null,
  duration: null,

  sectionInfo: {},
  segmentInfo: {
    verse1cam: null,
    verse2cam: null,
  },

  lyrics: [],
  verse1Timeline: [],
  verse2Timeline: {
    level1: [],
    level2: [],
    level3: [],
  },

  // 부분 업데이트
  setAll: (p) => set(p),

  // API 응답 전체 주입
  setFromApi: (resp) => {
    const d = resp.gameInfo;
    set({
      sessionId: d.sessionId,
      songId: d.songId,
      songTitle: d.songTitle,
      songArtist: d.songArtist,

      audioUrl: d.audioUrl,
      videoUrls: d.videoUrls,
      bpm: d.bpm,
      duration: d.duration,

      sectionInfo: d.sectionInfo,
      segmentInfo: {
        verse1cam: d.segmentInfo.verse1cam,
        verse2cam: d.segmentInfo.verse2cam,
      },

      lyrics: d.lyricsInfo,
      verse1Timeline: d.verse1Timeline,
      verse2Timeline: d.verse2Timeline,
    });
  },

  // 초기화
  clear: () =>
    set({
      sessionId: null,
      songId: null,
      songTitle: null,
      songArtist: null,

      audioUrl: null,
      videoUrls: {},
      bpm: null,
      duration: null,

      sectionInfo: {},
      segmentInfo: {
        verse1cam: null,
        verse2cam: null,
      },

      lyrics: [],
      verse1Timeline: [],
      verse2Timeline: {
        level1: [],
        level2: [],
        level3: [],
      },
    }),
}));