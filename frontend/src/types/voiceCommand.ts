// 음성 명령 응답 타입 정의

export interface SongInfo {
  songId: number;
  title: string;
  artist: string;
  mediaId: number;
  audioUrl: string;
  mode: 'LISTENING' | 'GAME';
}

export interface GameData {
  sessionId: string;
  songId: number;
  songTitle: string;
  songArtist: string;
  audioUrl: string;
  videoUrls: {
    intro: string;
    verse1: string;
    verse2_level1: string;
    verse2_level2: string;
    verse2_level3: string;
  };
  bpm: number;
  duration: number;
  sectionInfo: {
    introStartTime: number;
    verse1StartTime: number;
    breakStartTime: number;
    verse2StartTime: number;
    verse1SegmentStartTimes: number[];
    verse2SegmentStartTimes: number[];
  };
  lyricsInfo: {
    songId: number;
    title: string;
    verse1: any[];
    verse2: any[];
  };
}

export interface ListeningData {
  songId: number;
  autoPlay: boolean;
}

export interface ScreenTransition {
  targetScreen: string;
  action: string;
  data: GameData | ListeningData | any; // 케이스별로 다른 데이터
}

export interface VoiceCommandResponse {
  success: boolean;
  intent: string;
  responseText: string;
  ttsAudioUrl: string | null; // Base64 Data URI 또는 null
  songInfo: SongInfo | null;
  screenTransition: ScreenTransition | null;
}

export interface VoiceCommandError {
  message: string;
  code?: string;
}