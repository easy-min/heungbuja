import { create } from 'zustand';

interface AudioStore {
  audioRef: HTMLAudioElement | null;
  isPlaying: boolean;
  setAudioRef: (ref: HTMLAudioElement | null) => void;
  setIsPlaying: (playing: boolean) => void;
  pause: () => void;
  play: () => void;
}

export const useAudioStore = create<AudioStore>((set, get) => ({
  audioRef: null,
  isPlaying: false,

  setAudioRef: (ref) => set({ audioRef: ref }),

  setIsPlaying: (playing) => set({ isPlaying: playing }),

  pause: () => {
    const { audioRef } = get();
    console.log('🔊 audioStore.pause() 호출됨, audioRef:', audioRef);
    if (audioRef) {
      console.log('⏸️ audioRef.pause() 실행');
      audioRef.pause();
      set({ isPlaying: false });
    } else {
      console.log('⚠️ audioRef가 없습니다');
    }
  },

  play: () => {
    const { audioRef } = get();
    if (audioRef) {
      audioRef.play();
      set({ isPlaying: true });
    }
  },
}));
