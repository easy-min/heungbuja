import { type Frame, type Segment, type SegmentMetadata} from '@/types';
import { GAME_CONFIG } from './constants';
import JSZip from 'jszip';

/**
 * Data URI 문자열을 Blob으로 변환
 */
export const dataURItoBlob = (dataURI: string): Blob => {
  const byteString = atob(dataURI.split(',')[1]);
  const mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0];
  const ab = new ArrayBuffer(byteString.length);
  const ia = new Uint8Array(ab);
  
  for (let i = 0; i < byteString.length; i++) {
    ia[i] = byteString.charCodeAt(i);
  }
  
  return new Blob([ab], { type: mimeString });
};

export interface SegmentZipMeta {
  section: 'verse1' | 'verse2';
  segmentIndex: number;
  startTime: number;
  endTime: number;
  bpm: number;
  fps: number;
  frameCount: number;
  createdAt: string;
  title?: string;
}

/**
 * 세그먼트 데이터를 FormData로 변환
 */
export const createSegmentFormData = (
  segment: Segment,
  metadata: Omit<SegmentMetadata, 'segmentIndex' | 'frameCount' | 'musicTimeStart' | 'musicTimeEnd'>
): FormData => {
  const formData = new FormData();
  const segmentIndex = segment.index + 1; // 0-based → 1-based

  // 메타데이터 추가
  formData.append('segmentIndex', segmentIndex.toString());
  formData.append('sessionId', metadata.sessionId);
  formData.append('musicTitle', metadata.musicTitle);
  formData.append('songId', metadata.songId);
  formData.append('fps', metadata.fps.toString());
  formData.append('frameCount', segment.frames.length.toString());
  formData.append('verse', metadata.verse.toString());
  formData.append('captureTimestamp', metadata.captureTimestamp);

  // 음악 시간 (첫 프레임 ~ 마지막 프레임)
  if (segment.frames.length > 0) {
    const firstFrame = segment.frames[0];
    const lastFrame = segment.frames[segment.frames.length - 1];
    formData.append('musicTimeStart', firstFrame.musicTime.toFixed(3));
    formData.append('musicTimeEnd', lastFrame.musicTime.toFixed(3));
  }

  // 난이도 (2절일 때만)
  if (metadata.difficulty !== undefined) {
    formData.append('difficulty', metadata.difficulty.toString());
  }

  // 프레임 이미지들 추가
  segment.frames.forEach((frame, i) => {
    const blob = frame.img instanceof Blob ? frame.img : dataURItoBlob(frame.img as string);
    const filename = `seg${String(segmentIndex).padStart(2, '0')}_frame_${i}.jpg`;
    formData.append('frames', blob, filename);
  });

  return formData;
};

/**
 * 세션 ID 생성 (UUID)
 */
export const generateSessionId = (): string => {
  return crypto.randomUUID();
};

/**
 * 현재 시간을 ISO 8601 형식으로 반환
 */
export const getCurrentTimestamp = (): string => {
  return new Date().toISOString();
};

/**
 * 예상 프레임 개수 계산
 */
export const calculateExpectedFrames = (
  startTime: number,
  endTime: number,
  currentTime: number
): number => {
  const duration = endTime - Math.max(currentTime, startTime);
  return Math.ceil(duration * GAME_CONFIG.FPS) + 2; // 여유분 +2
};

/**
 * 파일 크기를 읽기 쉬운 형식으로 변환
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';
  
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
};
/**
 * 세그먼트를 ZIP 파일로 다운로드 (테스트용)
 */
export async function createSegmentZip(
  frames: Frame[],
  meta: SegmentZipMeta
): Promise<Blob> {
  const zip = new JSZip();

  zip.file(
    'metadata.json',
    JSON.stringify(
      {
        ...meta,
        frames: frames.map((f, i) => ({
          index: i,
          musicTime: f.musicTime,
          captureTimeMs: Math.round(f.captureTime),
        })),
      },
      null,
      2
    )
  );

  frames.forEach((f, i) => {
    const filename = `frame_${String(i + 1).padStart(4, '0')}.jpg`;
    zip.file(filename, f.img);
  });

  const blob = await zip.generateAsync({
    type: 'blob',
    compression: 'DEFLATE',
    compressionOptions: { level: 6 },
  });

  return blob;
}