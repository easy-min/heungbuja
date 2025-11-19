/**
 * 곡 간편등록 타입 정의
 */

// 단계
export type Step = 1 | 2 | 3 | 4;

// 비트 데이터
export interface Beat {
  bar: number;
  beat: number;
  t: number; // 시간 (초)
}

export interface TempoMap {
  bpm: number;
}

export interface BeatsData {
  tempoMap: TempoMap[];
  beats: Beat[];
}

// 가사 데이터
export interface LyricsLine {
  lineIndex: number;
  text: string;
  start: number; // 시작 시간 (초)
  end: number;   // 종료 시간 (초)
  sBeat: number; // 시작 비트
  eBeat: number; // 종료 비트
}

export interface LyricsData {
  lines: LyricsLine[];
}

// 분석 결과
export interface AnalyzedData {
  beats: BeatsData;
  lyrics: LyricsData;
  duration: number; // 곡 길이 (초)
}

// API 응답
export interface AnalyzeResponse {
  beats: BeatsData;
  lyrics: LyricsData;
  duration: number;
}

export interface RegisterResponse {
  songId: number;
  title: string;
  artist: string;
}

// 훅 반환 타입
export interface UseSimpleSongUpload {
  // 상태
  step: Step;
  audioFile: File | null;
  lyricsFile: File | null;
  title: string;
  artist: string;
  s3Key: string;
  isAnalyzing: boolean;
  progress: number;
  progressText: string;
  analyzedData: AnalyzedData | null;
  isRegistering: boolean;
  registeredSongId: number | null;
  error: string | null;
  
  // 액션
  setAudioFile: (file: File | null) => void;
  setLyricsFile: (file: File | null) => void;
  setTitle: (title: string) => void;
  setArtist: (artist: string) => void;
  setS3Key: (s3Key: string) => void;
  nextStep: () => void;
  prevStep: () => void;
  resetState: () => void;
  startAnalysis: () => Promise<void>;
  confirmRegister: () => Promise<void>;
}

// 컴포넌트 Props
export interface SimpleSongUploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

export interface SuccessStepProps {
  onGoToVisualization?: () => void;
}
