export interface SongInfo {
  audioUrl: string;
  title: string;
  artist: string;
  bpm: number;
  duration: number;
}

export interface SongTimeline {
  introStartTime: number;
  verse1StartTime: number;
  breakStartTime: number;
  verse2StartTime: number;
}

export interface SegmentRange { startTime: number; endTime: number; }

export interface LyricLine {
  text: string;
  startTime: number;
  endTime: number;
}

export interface GameStartResponse {
  success: boolean;
  data: {
    sessionId: string;
    songInfo: SongInfo;
    timeline: SongTimeline;
    segments: {
      verse1: SegmentRange;
      verse2: SegmentRange;
    };
    lyrics: LyricLine[];
    videoUrls: Record<string, string>;
  };
}