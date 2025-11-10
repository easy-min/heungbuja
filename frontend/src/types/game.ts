// ===== 프레임 관련 타입 =====

export interface Frame {
  img: Blob;              // JPEG 이미지 Blob
  musicTime: number;      // 음악 재생 시간 (초)
  captureTime: number;    // 캡처된 시점 (ms, 디버깅용)
}

// ===== 세그먼트 관련 타입 =====

export interface Segment {
  index: number;          // 세그먼트 인덱스 (0~5)
  frames: Frame[];        // 프레임 배열
}

export interface SegmentMetadata {
  segmentIndex: number;       // 세그먼트 번호 (1~6)
  musicTitle: string;         // 곡 제목
  songId: string;             // 곡 ID
  fps: number;                // 프레임레이트 (24)
  frameCount: number;         // 프레임 개수
  musicTimeStart: string;     // 시작 시간 (초, 소수점 3자리)
  musicTimeEnd: string;       // 종료 시간 (초, 소수점 3자리)
  verse: 1 | 2;               // 1절/2절
  sessionId: string;          // 세션 ID (UUID)               -> 백엔드에서 보내줄 예정
  captureTimestamp: string;   // 캡처 시작 시간 (ISO 8601)
  difficulty?: number;        // 난이도 (2절용, 옵션)
}

// ===== 음악/타이밍 관련 타입 =====

export interface Beat {
  bar: number;      // 마디 번호
  beat: number;     // 비트 번호 (1~4)
  t: number;        // 시간 (초)
}

export interface Section {
  label: string;    // 섹션 이름 (예: "part1")
  startBar: number; // 시작 마디
  endBar: number;   // 종료 마디
}

export interface BarGroup {
  segmentIndex: number;  // 세그먼트 번호 (1~6)
  startBar: number;      // 시작 마디
  endBar: number;        // 종료 마디
  startTime: number;     // 시작 시간 (초)
  endTime: number;       // 종료 시간 (초)
}

export interface SongData {
  beats: Beat[];
  sections: Section[];
}

export interface LyricLine {
  lineIndex: number;
  text: string;
  start: number; // (초)
  end: number;   // (초)
  sBeat: number;
  eBeat: number;
}

// ===== 업로드 관련 타입 =====

export interface UploadSegment {
  index: number;         // 세그먼트 인덱스
  frames: Frame[];       // 프레임 배열
  metadata?: Partial<SegmentMetadata>; // 추가 메타데이터
}

export interface UploadResponse { // TODO: 백엔드 응답 형식으로 수정 필요
  success: boolean;
  message: string;
  segmentIndex: number;
  similarity?: number;    // 유사도 점수 (백엔드에서 반환)
}

// ===== 카메라 관련 타입 =====

export interface CameraConfig {
  width: number;          // 비디오 너비 (기본: 320)
  height: number;         // 비디오 높이 (기본: 240)
  fps: number;            // 프레임레이트 (기본: 24)
}

export interface CameraState {
  stream: MediaStream | null;
  isReady: boolean;
  error: string | null;
}

// ===== 캡처 관련 타입 =====

export interface CaptureState {
  isCapturing: boolean;
  frameBuffer: Frame[];
  expectedFrames: number;
  captureStartTime: number;
  encoding: boolean;
}


// ===== 게임 설정 상수 타입 =====

export interface GameConfig {
  FPS: number;              // 프레임레이트 (24)
  FRAME_MS: number;         // 프레임당 ms (1000/24)
  EPS: number;              // 타이밍 오차 허용 (0.03초)
  LATE_GUARD: number;       // 늦은 시작 방지 (0.02초)
  MAX_RETRIES: number;      // 최대 재시도 횟수 (3)
  BARS_PER_SEGMENT: number; // 세그먼트당 마디 수 (4)
  SEGMENT_COUNT: number;    // 총 세그먼트 수 (6)
}