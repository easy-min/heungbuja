import { type Beat, type Section, type BarGroup, type Segment, type SegmentMetadata} from '@/types';
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

/**
 * JSON 데이터에서 BarGroup 계산
 */
export const calculateBarGroups = (
  beats: Beat[],
  sections: Section[]
): BarGroup[] => {
  // 1. 마디별 시작 시간 매핑
  const barTimes: Record<number, number> = {};
  let maxBar = 0;

  beats.forEach((b) => {
    if (b.beat === 1) {
      barTimes[b.bar] = b.t;
      if (b.bar > maxBar) maxBar = b.bar;
    }
  });

  // 2. part1 시작 마디 찾기 (1절 시작점)
  const part1 = sections.find((s) => s.label === 'verse1');
  if (!part1) {
    throw new Error('part1 섹션을 찾을 수 없습니다.');
  }

  // 3. 인트로 4마디 건너뛰고 시작
  const verseStartBar = part1.startBar + 4;

  // 4. 6개 세그먼트 계산 (4마디씩)
  const groups: BarGroup[] = [];
  
  for (let i = 0; i < GAME_CONFIG.SEGMENT_COUNT; i++) {
    const startBar = verseStartBar + i * GAME_CONFIG.BARS_PER_SEGMENT;
    const endBar = startBar + GAME_CONFIG.BARS_PER_SEGMENT - 1;
    const startTime = barTimes[startBar];
    const endTime = barTimes[endBar + 1] || beats[beats.length - 1].t;

    if (startTime === undefined) {
      throw new Error(`마디 ${startBar}의 시작 시간을 찾을 수 없습니다.`);
    }

    groups.push({
      segmentIndex: i + 1,
      startBar,
      endBar,
      startTime,
      endTime,
    });
  }

  return groups;
};

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
export const downloadSegmentAsZip = async (
  segment: Segment,
  segmentIndex: number
): Promise<void> => {
  const zip = new JSZip();
  
  // 메타데이터
  const metadata = {
    segmentIndex: segmentIndex + 1,
    frameCount: segment.frames.length,
    musicTimeStart: segment.frames[0]?.musicTime.toFixed(3) || '0.000',
    musicTimeEnd: segment.frames.at(-1)?.musicTime.toFixed(3) || '0.000',
    captureTimestamp: new Date().toISOString(),
  };
  
  zip.file('metadata.json', JSON.stringify(metadata, null, 2));
  
  // 프레임 이미지들
  for (let i = 0; i < segment.frames.length; i++) {
    const filename = `frame_${String(i).padStart(3, '0')}.jpg`;
    zip.file(filename, segment.frames[i].img);
  }
  
  // ZIP 생성
  console.log(`🔧 ZIP 파일 생성 중... (${segment.frames.length}개 프레임)`);
  const blob = await zip.generateAsync({ 
    type: 'blob',
    compression: 'DEFLATE',
    compressionOptions: { level: 6 }
  });
  
  // 다운로드
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `segment_${String(segmentIndex + 1).padStart(2, '0')}.zip`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  
  console.log(`✅ 세그먼트 ${segmentIndex + 1} ZIP 다운로드 완료`);
};