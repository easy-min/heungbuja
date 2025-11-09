// 음성 명령 응답 타입 정의

export interface SongInfo {
  songId: number;
  title: string;
  artist: string;
  mediaId: number;
  audioUrl: string;
  mode: 'LISTENING' | 'GAME';
}

export interface ScreenTransition {
  targetScreen: string;
  action: string;
  data: any; // 케이스별로 다른 데이터가 올 수 있음
}

export interface VoiceCommandResponse {
  success: boolean;
  intent: string;
  responseText: string;
  ttsAudioUrl: string;
  songInfo: SongInfo | null;
  screenTransition: ScreenTransition | null;
}

export interface VoiceCommandError {
  message: string;
  code?: string;
}