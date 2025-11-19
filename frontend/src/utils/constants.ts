import { type GameConfig  } from '@/types';

// ===== 체조 설정 상수 =====
export const GAME_CONFIG: GameConfig = {
  FPS: 10, // 30에서 10로 변경
  FRAME_MS: 1000 / 15, // 30에서 15로 변경
  EPS: 0.03,            // 타이밍 오차 허용 (30ms)
  LATE_GUARD: 0.08,     // 늦은 시작 방지 
  MAX_RETRIES: 3,       // 최대 재시도 횟수
  BARS_PER_SEGMENT: 4,  // 세그먼트당 마디 수
  SEGMENT_COUNT: 6,     // 총 세그먼트 수
};

// ===== 웨이크워드 =====
export const WAKE_WORD = '흥부야';

// ===== 카메라 설정 =====
export const CAMERA_CONFIG = {
  width: 180,
  height: 240,
  fps: GAME_CONFIG.FPS,
};

// ===== 로컬 스토리지 키 =====
export const STORAGE_KEYS = {
  USER_ID: 'heungbuja_userId',
  USER_NAME: 'heungbuja_userName',
} as const;
