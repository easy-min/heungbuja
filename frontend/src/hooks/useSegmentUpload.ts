import { useState, useRef, useCallback, useEffect } from 'react';
import { type Segment, type UploadResponse, type SegmentMetadata } from '@/types';
import { createSegmentFormData, createSegmentZip } from '@/utils/gameHelpers';
import { GAME_CONFIG, API_BASE_URL } from '@/utils/constants';

interface UseSegmentUploadProps {
  sessionId: string;
  songId: string;
  musicTitle: string;
  verse: 1 | 2;
  bpm: number;
  difficulty?: number;
  testMode?: boolean;
  onUploadSuccess?: (segmentIndex: number, response?: UploadResponse) => void;
  onUploadError?: (segmentIndex: number, error: Error) => void;
}

interface UseSegmentUploadReturn {
  uploadQueue: Segment[];
  isUploading: boolean;
  queueSegmentUpload: (segment: Segment) => void;
}

export const useSegmentUpload = ({
  sessionId,
  songId,
  musicTitle,
  verse,
  bpm,
  difficulty,
  testMode = true,
  onUploadSuccess,
  onUploadError,
}: UseSegmentUploadProps): UseSegmentUploadReturn => {
  const [uploadQueue, setUploadQueue] = useState<Segment[]>([]);
  const [isUploading, setIsUploading] = useState(false);

  const retryMapRef = useRef<Map<number, number>>(new Map());
  const isProcessingRef = useRef(false);

  const queueSegmentUpload = useCallback((segment: Segment): void => {
    console.log(`📦 세그먼트 ${segment.index + 1} 큐에 추가 (${segment.frames.length} 프레임)`);
    setUploadQueue((prev) => [...prev, segment]);
  }, []);

  const processUploadQueue = useCallback(async (): Promise<void> => {
    if (isProcessingRef.current || uploadQueue.length === 0) return;

    isProcessingRef.current = true;
    setIsUploading(true);

    const segment = uploadQueue[0];
    const segmentIndex = segment.index + 1;

    try {
      if (testMode) {
        // 테스트 모드: ZIP 생성 후 로컬 다운로드
        console.log(`🧪 [테스트 모드] 세그먼트 ${segmentIndex} ZIP 생성...`);

        const section = verse === 1 ? 'verse1' : 'verse2';

        const firstT = segment.frames[0]?.musicTime ?? 0;
        const lastT  = segment.frames.at(-1)?.musicTime ?? firstT;

        const meta = {
          section,
          segmentIndex,            // 1-based로 저장
          startTime: firstT,
          endTime: lastT,
          bpm,
          fps: GAME_CONFIG.FPS,
          frameCount: segment.frames.length,
          createdAt: new Date().toISOString(),
          title: musicTitle,
        } as const;

        const zipBlob = await createSegmentZip(segment.frames, meta);

        const safeTitle = (musicTitle || 'song').replace(/[^\w.-]+/g, '_');
        const filename = `${safeTitle}_${section}_${String(segmentIndex).padStart(2, '0')}_16beats_${GAME_CONFIG.FPS}fps.zip`;

        const url = URL.createObjectURL(zipBlob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        console.log(`✅ [테스트 모드] 세그먼트 ${segmentIndex} ZIP 다운로드 완료`);
        onUploadSuccess?.(segmentIndex);

        setUploadQueue((prev) => prev.slice(1));

      } else {
        // 프로덕션 모드: 백엔드 업로드
        console.log(`📤 세그먼트 ${segmentIndex} 업로드 시작...`);

        const metadata: Omit<SegmentMetadata, 'segmentIndex' | 'frameCount' | 'musicTimeStart' | 'musicTimeEnd'> = {
          sessionId,
          songId,
          musicTitle,
          fps: GAME_CONFIG.FPS,
          verse,
          captureTimestamp: new Date().toISOString(),
          difficulty,
        };

        const formData = createSegmentFormData(segment, metadata);

        const response = await fetch(`${API_BASE_URL}/upload`, {
          method: 'POST',
          body: formData,
        });
        if (!response.ok) throw new Error(`서버 응답 실패: ${response.status}`);

        const result: UploadResponse = await response.json();
        console.log(`✅ 세그먼트 ${segmentIndex} 업로드 완료`, result);

        onUploadSuccess?.(segmentIndex, result);
        setUploadQueue((prev) => prev.slice(1));
        retryMapRef.current.delete(segment.index);
      }
    } catch (e) {
      const error = e instanceof Error ? e : new Error('처리 실패');
      console.error(`❌ 세그먼트 ${segmentIndex} 실패:`, error);

      if (testMode) {
        console.warn(`🚫 [테스트 모드] 세그먼트 ${segmentIndex} 실패, 건너뜀`);
        onUploadError?.(segmentIndex, error);
        setUploadQueue((prev) => prev.slice(1));
      } else {
        const retryCount = retryMapRef.current.get(segment.index) || 0;
        if (retryCount < GAME_CONFIG.MAX_RETRIES) {
          retryMapRef.current.set(segment.index, retryCount + 1);
          console.warn(`⚠️ 재시도 ${retryCount + 1}/${GAME_CONFIG.MAX_RETRIES}`);
          setUploadQueue((prev) => [...prev.slice(1), segment]);
        } else {
          console.error(`🚫 재시도 초과, 건너뜀`);
          onUploadError?.(segmentIndex, error);
          setUploadQueue((prev) => prev.slice(1));
        }
      }
    } finally {
      isProcessingRef.current = false;
      setIsUploading(false);
      setTimeout(() => {
        if (uploadQueue.length > 1) processUploadQueue();
      }, 100);
    }
  }, [uploadQueue, sessionId, songId, musicTitle, verse, bpm, difficulty, testMode, onUploadSuccess, onUploadError]);

  useEffect(() => {
    if (uploadQueue.length > 0 && !isProcessingRef.current) {
      processUploadQueue();
    }
  }, [uploadQueue, processUploadQueue]);

  return { uploadQueue, isUploading, queueSegmentUpload };
};