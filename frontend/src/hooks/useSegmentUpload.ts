import { useState, useRef, useCallback, useEffect } from 'react';
import { type Segment, type UploadResponse, type SegmentMetadata } from '@/types';
import { createSegmentFormData, downloadSegmentAsZip } from '@/utils/gameHelpers';
import { GAME_CONFIG, API_BASE_URL } from '@/utils/constants';

interface UseSegmentUploadProps {
  sessionId: string;
  songId: string;
  musicTitle: string;
  verse: 1 | 2;
  difficulty?: number;
  testMode?: boolean;  // ✅ 테스트 모드 추가
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
  difficulty,
  testMode = true,  // ✅ 기본값 true
  onUploadSuccess,
  onUploadError,
}: UseSegmentUploadProps): UseSegmentUploadReturn => {
  const [uploadQueue, setUploadQueue] = useState<Segment[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  
  const retryMapRef = useRef<Map<number, number>>(new Map());
  const isProcessingRef = useRef(false);

  /**
   * 세그먼트를 업로드 큐에 추가
   */
  const queueSegmentUpload = useCallback((segment: Segment): void => {
    console.log(`📦 세그먼트 ${segment.index + 1} 큐에 추가 (${segment.frames.length} 프레임)`);
    
    setUploadQueue((prev) => [...prev, segment]);
  }, []);

  /**
   * 업로드 큐 처리 (테스트 모드 지원)
   */
  const processUploadQueue = useCallback(async (): Promise<void> => {
    if (isProcessingRef.current || uploadQueue.length === 0) return;

    isProcessingRef.current = true;
    setIsUploading(true);

    const segment = uploadQueue[0];
    const segmentIndex = segment.index + 1; // 0-based → 1-based

    try {
      if (testMode) {
        // ✅ 테스트 모드: ZIP 다운로드
        console.log(`🧪 [테스트 모드] 세그먼트 ${segmentIndex} ZIP 다운로드 시작...`);
        
        await downloadSegmentAsZip(segment, segment.index);
        
        console.log(`✅ [테스트 모드] 세그먼트 ${segmentIndex} 다운로드 완료`);
        
        // 성공 콜백 (response 없음)
        onUploadSuccess?.(segmentIndex);
        
        // 큐에서 제거
        setUploadQueue((prev) => prev.slice(1));
        
      } else {
        // ✅ 프로덕션 모드: 백엔드 업로드
        console.log(`📤 세그먼트 ${segmentIndex} 업로드 시작...`);

        // 메타데이터 생성
        const metadata: Omit<SegmentMetadata, 'segmentIndex' | 'frameCount' | 'musicTimeStart' | 'musicTimeEnd'> = {
          sessionId,
          songId,
          musicTitle,
          fps: GAME_CONFIG.FPS,
          verse,
          captureTimestamp: new Date().toISOString(),
          difficulty,
        };

        // FormData 생성
        const formData = createSegmentFormData(segment, metadata);

        // 백엔드 전송
        const response = await fetch(`${API_BASE_URL}/upload`, {
          method: 'POST',
          body: formData,
        });

        if (!response.ok) {
          throw new Error(`서버 응답 실패: ${response.status}`);
        }

        const result: UploadResponse = await response.json();
        
        console.log(`✅ 세그먼트 ${segmentIndex} 업로드 완료`, result);
        
        // 성공 콜백
        onUploadSuccess?.(segmentIndex, result);
        
        // 큐에서 제거
        setUploadQueue((prev) => prev.slice(1));
        
        // 재시도 카운터 초기화
        retryMapRef.current.delete(segment.index);
      }

    } catch (err) {
      const error = err instanceof Error ? err : new Error('처리 실패');
      console.error(`❌ 세그먼트 ${segmentIndex} 실패:`, error);

      if (testMode) {
        // 테스트 모드에서는 재시도 없이 스킵
        console.warn(`🚫 [테스트 모드] 세그먼트 ${segmentIndex} 실패, 건너뜀`);
        onUploadError?.(segmentIndex, error);
        setUploadQueue((prev) => prev.slice(1));
        
      } else {
        // 프로덕션 모드: 재시도 로직
        const retryCount = retryMapRef.current.get(segment.index) || 0;

        if (retryCount < GAME_CONFIG.MAX_RETRIES) {
          // 재시도
          retryMapRef.current.set(segment.index, retryCount + 1);
          console.warn(`⚠️  세그먼트 ${segmentIndex} 재시도 (${retryCount + 1}/${GAME_CONFIG.MAX_RETRIES})`);
          
          // 큐의 맨 뒤로 이동
          setUploadQueue((prev) => [...prev.slice(1), segment]);
        } else {
          // 재시도 횟수 초과
          console.error(`🚫 세그먼트 ${segmentIndex} 재시도 횟수 초과, 건너뜀`);
          
          // 에러 콜백
          onUploadError?.(segmentIndex, error);
          
          // 큐에서 제거
          setUploadQueue((prev) => prev.slice(1));
        }
      }
    } finally {
      isProcessingRef.current = false;
      setIsUploading(false);
      
      // 다음 세그먼트 처리 (약간의 딜레이)
      setTimeout(() => {
        if (uploadQueue.length > 1) {
          processUploadQueue();
        }
      }, 100);
    }
  }, [uploadQueue, sessionId, songId, musicTitle, verse, difficulty, testMode, onUploadSuccess, onUploadError]);

  /**
   * 큐가 변경될 때마다 처리 시작
   */
  useEffect(() => {
    if (uploadQueue.length > 0 && !isProcessingRef.current) {
      processUploadQueue();
    }
  }, [uploadQueue, processUploadQueue]);

  return {
    uploadQueue,
    isUploading,
    queueSegmentUpload,
  };
};