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

export interface SongTimeline {
  introStartTime: number;
  verse1StartTime: number;
  breakStartTime: number;
  verse2StartTime: number;
}

export interface GameStartResponse {
  intent: string;
  gameInfo:  {
    sessionId: string;
    songId: number;
    songTitle: string;
    songArtist: string;
    audioUrl: string;
    videoUrls: {
      intro: string,
      verse1: string,
      verse2_level1: string,
      verse2_level2: string,
      verse2_level3: string,
    };
    bpm: number;
    duration: number;
    sectionInfo: SongTimeline;
    segmentInfo: {
      verse1cam: SegmentRange;
      verse2cam: SegmentRange;
    };
    lyricsInfo: {
      id: string;
      lines: LyricLine[];
    }
    verse1Timeline: actionLine[];
    verse2Timelines: {
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

export interface GameEndResponse {
  finalScore: number;
  message: string;
}

type Judgment = 1 | 2 | 3; // 1: SOSO, 2: GOOD, 3: PERFECT
export interface FeedbackMessage {
  type: 'FEEDBACK';
  data: { judgment: Judgment; timestamp: number };
}