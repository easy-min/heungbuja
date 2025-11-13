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