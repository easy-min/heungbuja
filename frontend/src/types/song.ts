// 노래 관련 타입 정의

export interface SongInfo {
  songId: number;
  title: string;
  artist: string;
  mediaId: number;
  audioUrl: string;
  mode: 'LISTENING' | 'EXERCISE';
}

export type Song = {
  id: number;
  rank: number;
  title: string;
  artist: string;
};
