// 곡 기본 정보
export interface Song {
  id: number;
  title: string;
  artist: string;
}

// 비트 데이터
export interface Beat {
  i: number;        // 비트 인덱스
  t: number;        // 시간(초)
  bar: number;      // 마디 번호
  beat: number;     // 비트 (1-4)
}

export interface TempoMap {
  t: number;        // 시간(초)
  bpm: number;      // BPM
}

export interface Section {
  label: string;    // 구간명 (intro, verse1, break, verse2 등)
  startBeat: number;
  endBeat: number;
}

export interface SongBeat {
  beats: Beat[];
  tempoMap: TempoMap[];
  sections: Section[];
}

// 가사 데이터
export interface LyricLine {
  text: string;     // 가사 텍스트
  start: number;    // 시작 시간(초)
  end: number;      // 종료 시간(초)
  sbeat: number;    // 시작 비트 인덱스
  ebeat: number;    // 종료 비트 인덱스
}

export interface LyricsInfo {
  lines: LyricLine[];
}

// 동작 데이터
export interface Action {
  time: number;         // 동작 실행 시간(초)
  actionCode: number;   // 동작 코드 (0-7)
  actionName: string;   // 동작 이름
}

// 시각화 전체 데이터
export interface VisualizationData {
  bpm: number;
  duration: number;
  songBeat: SongBeat;
  lyricsInfo: LyricsInfo;
  verse1Timeline: Action[];
  verse2Timeline?: Action[];
}

// 타임라인 아이템 상태
export type TimelineStatus = 'past' | 'current' | 'upcoming';

// 동작 아이콘 매핑 타입
export interface ActionIconMap {
  [key: number]: string;
}
