import { create } from 'zustand';
import type {
  Song,
  VisualizationData,
  Beat,
  LyricLine,
  Action,
} from '../types/visualization';

interface VisualizationStore {
  // 곡 목록
  songs: Song[];
  isLoadingSongs: boolean;

  // 선택된 곡
  selectedSongId: number | null;
  visualizationData: VisualizationData | null;
  isLoadingVisualization: boolean;

  // 재생 상태
  isPlaying: boolean;
  currentTime: number;

  // 현재 위치 정보
  currentBeat: Beat | null;
  currentLyric: LyricLine | null;
  currentSection: string;
  currentAction: Action | null;

  // 난이도 선택
  selectedLevel: 'level1' | 'level2' | 'level3';

  // 에러
  error: string | null;

  // 액션
  setSongs: (songs: Song[]) => void;
  setIsLoadingSongs: (loading: boolean) => void;
  setSelectedSongId: (id: number | null) => void;
  setVisualizationData: (data: VisualizationData | null) => void;
  setIsLoadingVisualization: (loading: boolean) => void;
  setIsPlaying: (playing: boolean) => void;
  setCurrentTime: (time: number) => void;
  setSelectedLevel: (level: 'level1' | 'level2' | 'level3') => void;
  setError: (error: string | null) => void;
  updatePlaybackPosition: (time: number) => void;
  reset: () => void;
}

export const useVisualizationStore = create<VisualizationStore>((set, get) => ({
  // 초기 상태
  songs: [],
  isLoadingSongs: false,
  selectedSongId: null,
  visualizationData: null,
  isLoadingVisualization: false,
  isPlaying: false,
  currentTime: 0,
  currentBeat: null,
  currentLyric: null,
  currentSection: 'Intro',
  currentAction: null,
  selectedLevel: 'level1',
  error: null,

  // Setter 액션
  setSongs: (songs) => set({ songs }),
  setIsLoadingSongs: (loading) => set({ isLoadingSongs: loading }),
  setSelectedSongId: (id) => set({ selectedSongId: id }),
  setVisualizationData: (data) => set({ visualizationData: data }),
  setIsLoadingVisualization: (loading) => set({ isLoadingVisualization: loading }),
  setIsPlaying: (playing) => set({ isPlaying: playing }),
  setCurrentTime: (time) => set({ currentTime: time }),
  setSelectedLevel: (level) => set({ selectedLevel: level }),
  setError: (error) => set({ error }),

  // 재생 위치 업데이트 (핵심 로직)
  updatePlaybackPosition: (time: number) => {
    const { visualizationData } = get();
    if (!visualizationData) return;

    set({ currentTime: time });

    // 1. 현재 비트 찾기
    const beats = visualizationData.songBeat.beats || [];
    let currentBeat: Beat | null = null;
    let minDiff = Infinity;

    for (const beat of beats) {
      const diff = Math.abs(beat.t - time);
      if (diff < minDiff && beat.t <= time + 0.05) {
        minDiff = diff;
        currentBeat = beat;
      }
    }

    // 2. 현재 가사 찾기
    const lyrics = visualizationData.lyricsInfo?.lines || [];
    const currentLyric = lyrics.find(
      (lyric) => time >= lyric.start && time < lyric.end
    ) || null;

    // 3. 현재 구간 찾기
    const sections = visualizationData.songBeat.sections || [];
    let currentSection = 'Intro';

    for (const section of sections) {
      const startBeat = beats.find((b) => b.i === section.startBeat);
      const endBeat = beats.find((b) => b.i === section.endBeat);
      if (startBeat && endBeat && time >= startBeat.t && time <= endBeat.t) {
        currentSection = section.label;
        break;
      }
    }

    // 4. 현재 동작 찾기 (±0.2초 범위)
    const timeline = visualizationData.verse1Timeline || [];
    const currentAction = timeline.find(
      (action) => Math.abs(action.time - time) < 0.2
    ) || null;

    // 상태 업데이트
    set({
      currentBeat,
      currentLyric,
      currentSection,
      currentAction,
    });
  },

  // 상태 초기화
  reset: () =>
    set({
      isPlaying: false,
      currentTime: 0,
      currentBeat: null,
      currentLyric: null,
      currentSection: 'Intro',
      currentAction: null,
    }),
}));
