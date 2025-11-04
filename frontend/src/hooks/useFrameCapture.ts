import { useState, useRef, useCallback } from 'react';
import { type Frame } from '@/types';
import { GAME_CONFIG, calculateExpectedFrames } from '@/utils';

interface UseFrameCaptureProps {
  videoRef: React.RefObject<HTMLVideoElement>;
  audioRef: React.RefObject<HTMLAudioElement>;
  canvasRef: React.RefObject<HTMLCanvasElement>;
}

interface UseFrameCaptureReturn {
  isCapturing: boolean;
  frameBuffer: Frame[];
  startCapture: (startTime: number, endTime: number) => void;
  stopCapture: () => Frame[];
}

export const useFrameCapture = ({
  videoRef,
  audioRef,
  canvasRef,
}: UseFrameCaptureProps): UseFrameCaptureReturn => {
  const [isCapturing, setIsCapturing] = useState(false);
  const [frameBuffer, setFrameBuffer] = useState<Frame[]>([]);
  
  const captureStartTimeRef = useRef<number>(0);
  const frameCountRef = useRef<number>(0);
  const expectedFramesRef = useRef<number>(0);
  const encodingRef = useRef<boolean>(false);
  const animationFrameIdRef = useRef<number | null>(null);

  /**
   * 프레임 캡처 시작
   */
  const startCapture = useCallback((startTime: number, endTime: number): void => {
    if (isCapturing || !videoRef.current || !audioRef.current || !canvasRef.current) {
      console.warn('⚠️  캡처 시작 실패: 이미 캡처 중이거나 ref가 없음');
      return;
    }

    const video = videoRef.current;
    const audio = audioRef.current;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');

    if (!ctx || video.readyState < 2) {
      console.warn('⚠️  캡처 시작 실패: canvas 또는 video 준비 안 됨');
      return;
    }

    const now = audio.currentTime;

    // 너무 늦게 시작하려는 경우 건너뛰기
    if (now > endTime - GAME_CONFIG.LATE_GUARD) {
      console.warn(`⏭ 세그먼트 건너뜀 (늦음: ${now.toFixed(2)} > ${endTime.toFixed(2)})`);
      return;
    }

    setIsCapturing(true);
    setFrameBuffer([]);
    
    captureStartTimeRef.current = performance.now();
    frameCountRef.current = 0;
    expectedFramesRef.current = calculateExpectedFrames(startTime, endTime, now);
    encodingRef.current = false;

    console.log(`📹 캡처 시작 (예상 프레임: ${expectedFramesRef.current})`);

    /**
     * requestAnimationFrame 기반 캡처 루프
     */
    const captureFrame = () => {
      if (!isCapturing) return;

      const elapsed = performance.now() - captureStartTimeRef.current;
      const targetFrame = Math.floor(elapsed / GAME_CONFIG.FRAME_MS);

      // 다음 프레임 시간까지 대기
      if (
        frameCountRef.current < targetFrame &&
        !encodingRef.current &&
        video.readyState >= 2
      ) {
        encodingRef.current = true;
        frameCountRef.current++;

        // canvas에 video 프레임 그리기
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

        // Blob으로 변환
        canvas.toBlob(
          (blob) => {
            if (!blob) {
              encodingRef.current = false;
              return;
            }

            const frame: Frame = {
              img: blob,
              musicTime: audio.currentTime,
              captureTime: elapsed,
            };

            setFrameBuffer((prev) => {
              const newBuffer = [...prev, frame];
              
              // 예상 프레임 도달 시 자동 중지
              if (newBuffer.length >= expectedFramesRef.current) {
                stopCapture();
              }
              
              return newBuffer;
            });

            encodingRef.current = false;
          },
          'image/jpeg',
          0.8
        );
      }

      if (isCapturing) {
        animationFrameIdRef.current = requestAnimationFrame(captureFrame);
      }
    };

    animationFrameIdRef.current = requestAnimationFrame(captureFrame);
  }, [isCapturing, videoRef, audioRef, canvasRef]);

  /**
   * 프레임 캡처 중지
   */
  const stopCapture = useCallback((): Frame[] => {
    if (!isCapturing) return [];

    setIsCapturing(false);

    // 애니메이션 프레임 취소
    if (animationFrameIdRef.current !== null) {
      cancelAnimationFrame(animationFrameIdRef.current);
      animationFrameIdRef.current = null;
    }

    const capturedFrames = frameBuffer;
    console.log(`⏹ 캡처 중지 (${capturedFrames.length} 프레임)`);
    
    return capturedFrames;
  }, [isCapturing, frameBuffer]);

  return {
    isCapturing,
    frameBuffer,
    startCapture,
    stopCapture,
  };
};