export interface SongTimeline {
  introStartTime: number;
  verse1StartTime: number;
  breakStartTime: number;
  verse2StartTime: number;
}

export interface SegmentRange { startTime: number; endTime: number; }

export interface LyricLine {
  lineIndex: number;
  text: string;
  start: number;
  end: number;
  sbeat: number;
  ebeat: number;
}

export interface actionLine {
  time: number;
  actionCode: number;
  actionName: string;
}

// Record 필드는 추후 수정 고민 필요
export interface GameStartResponse {
  intent: string;
  gameInfo:  {
    sessionId: string;
    songId: number;
    songTitle: string;
    songArtist: string;
    audioUrl: string;
    videoUrls: Record<string, string>;
    bpm: number;
    duration: number;
    sectionInfo: Record<string, number>
    segmentInfo: {
      verse1cam: SegmentRange;
      verse2cam: SegmentRange;
    };
    lyricsInfo: {
      id: string;
      lines: LyricLine[];
    }
    verse1Timeline: actionLine[];
    verse2Timeline: {
      level1: actionLine[];
      level2: actionLine[];
      level3: actionLine[];
    };
  };
}

export interface ListeningData {
  songId: number;
  autoPlay: boolean;
}