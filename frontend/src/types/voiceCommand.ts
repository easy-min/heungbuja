// 음성 명령 응답 타입 정의

import type { SongInfo } from './song';
import type { GameStartResponse, ListeningData } from './game';

export interface ScreenTransition {
  targetScreen: string;
  action: string;
  data: GameStartResponse | ListeningData | any; // 케이스별로 다른 데이터
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