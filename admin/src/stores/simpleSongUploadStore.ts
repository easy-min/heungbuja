import { create } from 'zustand';
import type { 
  Step, 
  AnalyzedData,
  AnalyzeResponse,
  RegisterResponse
} from '../types';

interface SimpleSongUploadStore {
  // UI 상태
  step: Step;
  
  // 파일
  audioFile: File | null;
  lyricsFile: File | null;
  
  // 메타데이터
  title: string;
  artist: string;
  s3Key: string;
  
  // 분석
  isAnalyzing: boolean;
  progress: number;
  progressText: string;
  analyzedData: AnalyzedData | null;
  
  // 등록
  isRegistering: boolean;
  registeredSongId: number | null;
  
  // 에러
  error: string | null;
  
  // 액션
  setStep: (step: Step) => void;
  setAudioFile: (file: File | null) => void;
  setLyricsFile: (file: File | null) => void;
  setTitle: (title: string) => void;
  setArtist: (artist: string) => void;
  setS3Key: (s3Key: string) => void;
  setIsAnalyzing: (isAnalyzing: boolean) => void;
  setProgress: (progress: number) => void;
  setProgressText: (text: string) => void;
  setAnalyzedData: (data: AnalyzedData | null) => void;
  setIsRegistering: (isRegistering: boolean) => void;
  setRegisteredSongId: (id: number | null) => void;
  setError: (error: string | null) => void;
  
  nextStep: () => void;
  prevStep: () => void;
  resetState: () => void;
  startAnalysis: () => Promise<void>;
  confirmRegister: () => Promise<void>;
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

// 토큰 가져오기
const getAccessToken = (): string => {
  return localStorage.getItem('accessToken') || '';
};

export const useSimpleSongUploadStore = create<SimpleSongUploadStore>((set, get) => ({
  // 초기 상태
  step: 1,
  audioFile: null,
  lyricsFile: null,
  title: '',
  artist: '',
  s3Key: '',
  isAnalyzing: false,
  progress: 0,
  progressText: '분석 준비 중...',
  analyzedData: null,
  isRegistering: false,
  registeredSongId: null,
  error: null,
  
  // Setter 액션
  setStep: (step) => set({ step }),
  setAudioFile: (audioFile) => set({ audioFile }),
  setLyricsFile: (lyricsFile) => set({ lyricsFile }),
  setTitle: (title) => set({ title }),
  setArtist: (artist) => set({ artist }),
  setS3Key: (s3Key) => set({ s3Key }),
  setIsAnalyzing: (isAnalyzing) => set({ isAnalyzing }),
  setProgress: (progress) => set({ progress }),
  setProgressText: (progressText) => set({ progressText }),
  setAnalyzedData: (analyzedData) => set({ analyzedData }),
  setIsRegistering: (isRegistering) => set({ isRegistering }),
  setRegisteredSongId: (registeredSongId) => set({ registeredSongId }),
  setError: (error) => set({ error }),
  
  // 단계 이동
  nextStep: () => set((state) => ({ 
    step: Math.min(state.step + 1, 4) as Step 
  })),
  
  prevStep: () => set((state) => ({ 
    step: Math.max(state.step - 1, 1) as Step 
  })),
  
  // 상태 초기화
  resetState: () => set({
    step: 1,
    audioFile: null,
    lyricsFile: null,
    title: '',
    artist: '',
    s3Key: '',
    isAnalyzing: false,
    progress: 0,
    progressText: '분석 준비 중...',
    analyzedData: null,
    isRegistering: false,
    registeredSongId: null,
    error: null,
  }),
  
  // 분석 시작
  startAnalysis: async () => {
    const state = get();
    
    try {
      set({ isAnalyzing: true, progress: 0, error: null, step: 3 });

      if (!state.audioFile || !state.lyricsFile) {
        throw new Error('파일이 선택되지 않았습니다.');
      }

      // Progress Bar 가짜 진행률
      const progressInterval = setInterval(() => {
        const currentProgress = get().progress;
        if (currentProgress < 85) {
          const newProgress = Math.min(currentProgress + 3, 85);
          set({ progress: newProgress });

          if (newProgress < 30) {
            set({ progressText: '오디오 파일 로딩 중...' });
          } else if (newProgress < 60) {
            set({ progressText: '비트 검출 중... (librosa)' });
          } else if (newProgress < 85) {
            set({ progressText: '가사 타이밍 분석 중...' });
          }
        }
      }, 400);

      const formData = new FormData();
      formData.append('title', state.title);
      formData.append('artist', state.artist);
      formData.append('s3Key', state.s3Key);
      formData.append('audioFile', state.audioFile);
      formData.append('lyricsFile', state.lyricsFile);

      const accessToken = getAccessToken();
      console.log('📤 분석 API 호출');

      const res = await fetch(`${API_BASE}/admins/songs/analyze-only`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`
        },
        body: formData
      });

      clearInterval(progressInterval);
      console.log('📥 응답 상태:', res.status);

      if (res.ok) {
        const result: AnalyzeResponse = await res.json();
        console.log('✅ 분석 성공:', result);

        set({ 
          progress: 100, 
          progressText: '분석 완료!',
          analyzedData: {
            beats: result.beats,
            lyrics: result.lyrics,
            duration: result.duration
          }
        });

        setTimeout(() => {
          set({ isAnalyzing: false });
        }, 500);

      } else {
        const error = await res.json().catch(() => ({ message: res.statusText }));
        console.error('❌ 분석 실패:', error);
        throw new Error(error.detail || error.message || res.statusText);
      }

    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : '알 수 없는 오류';
      console.error('❌ 분석 중 오류:', e);
      set({ error: errorMessage, isAnalyzing: false, step: 2 });
      alert(`분석 실패: ${errorMessage}\n\nmusic-server가 실행 중인지 확인해주세요.`);
    }
  },
  
  // 등록 확정
  confirmRegister: async () => {
    const state = get();
    
    try {
      set({ isRegistering: true, error: null });

      if (!state.audioFile || !state.lyricsFile) {
        throw new Error('파일이 선택되지 않았습니다.');
      }

      const formData = new FormData();
      formData.append('title', state.title);
      formData.append('artist', state.artist);
      formData.append('s3Key', state.s3Key);
      formData.append('audioFile', state.audioFile);
      formData.append('lyricsFile', state.lyricsFile);

      const accessToken = getAccessToken();
      console.log('📤 등록 API 호출');

      const res = await fetch(`${API_BASE}/admins/songs/auto`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`
        },
        body: formData
      });

      console.log('📥 응답 상태:', res.status);

      if (res.ok) {
        const result: RegisterResponse = await res.json();
        console.log('✅ 등록 성공:', result);

        set({ 
          registeredSongId: result.songId,
          step: 4 
        });

      } else {
        const error = await res.json().catch(() => ({ message: res.statusText }));
        console.error('❌ 등록 실패:', error);
        throw new Error(error.error || error.message || res.statusText);
      }

    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : '알 수 없는 오류';
      console.error('❌ 등록 중 오류:', e);
      set({ error: errorMessage });
      alert(`등록 실패: ${errorMessage}`);
    } finally {
      set({ isRegistering: false });
    }
  },
}));
