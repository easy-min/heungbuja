/* @ts-nocheck */
import { useState, useRef, useCallback } from 'react';
import { type Frame } from '@/types';
import { GAME_CONFIG, calculateExpectedFrames } from '@/utils';

interface UseFrameCaptureProps {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  audioRef: React.RefObject<HTMLAudioElement | null>;
  canvasRef: React.RefObject<HTMLCanvasElement | null>;
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
  const captureEpochRef = useRef(0); // 세그먼트(캡처 세션) 식별용
    // ✅ 수정 (UI용 state는 유지하되, 동작 제어는 ref로)
  const [isCapturing, setIsCapturing] = useState(false); // UI 표시용
  const isCapturingRef = useRef(false);                   // 실제 제어용
    // ✅ 수정
  const [frameBuffer, setFrameBuffer] = useState<Frame[]>([]); // UI용
  const frameBufferRef = useRef<Frame[]>([]);                  // 실데이터
  
  const captureStartTimeRef = useRef<number>(0);
  const frameCountRef = useRef<number>(0);
  const expectedFramesRef = useRef<number>(0);
  const encodingRef = useRef<boolean>(false);
  const animationFrameIdRef = useRef<number | null>(null);
  /**
   * 프레임 캡처 중지
   */
 // ✅ 수정
    const stopCapture = useCallback((): Frame[] => {
      // 1) 들어오자마자 현재 상태 로그
      // const before = {
      //   isCapturingRef: isCapturingRef.current,
      //   raf: animationFrameIdRef.current,
      //   encoding: encodingRef.current,
      //   frames: frameBufferRef.current.length,
      //   epoch: captureEpochRef.current,
      // };
      // console.log('[stopCapture] called', before);

      // 2) 이미 멈춰있으면 즉시 반환
      if (!isCapturingRef.current) {
        console.warn('[stopCapture] already stopped → []');
        return [];
      }

      // 3) 여기서 상태 내리기/취소/반환
      isCapturingRef.current = false;
      setIsCapturing(false);

      if (animationFrameIdRef.current !== null) {
        cancelAnimationFrame(animationFrameIdRef.current);
        animationFrameIdRef.current = null;
      }

      const capturedFrames = frameBufferRef.current;

      console.log('[stopCapture] ✅ STOP', {
        frames: capturedFrames.length,
        raf: animationFrameIdRef.current,
        encoding: encodingRef.current,
        epoch: captureEpochRef.current,
      });

      return capturedFrames;
    }, []); // ← 의존성 비워서 안정화
  /**
   * 프레임 캡처 시작
   */
  const startCapture = useCallback((startTime: number, endTime: number): void => {


  //     console.log('[startCapture] called', {
  //   isCapturingRef: isCapturingRef.current,
  //   videoReady: !!videoRef.current,
  //   audioReady: !!audioRef.current,
  //   canvasReady: !!canvasRef.current,
  // });
    // ✅ 세이프티 스톱 추가
  if (isCapturingRef.current) {
    console.warn('[startCapture] previous capture still active → safe stop before new start');
    stopCapture();   // 이전 세그먼트 캡처 완전히 종료
  }
    // ✅ 수정
    if (isCapturingRef.current || !videoRef.current || !audioRef.current || !canvasRef.current)
      {
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
    // console.log('🎯 TIMING CHECK', {
    //   now: now.toFixed(3),
    //   startTime: startTime.toFixed(3),
    //   endTime: endTime.toFixed(3),
    //   guard: GAME_CONFIG.LATE_GUARD,
    //   diffToEnd: (endTime - now).toFixed(3),
    // });
    // 너무 늦게 시작하려는 경우 건너뛰기
    if (now > endTime - GAME_CONFIG.LATE_GUARD) {
      console.warn(`⏭ 세그먼트 건너뜀 (늦음: ${now.toFixed(2)} > ${endTime.toFixed(2)})`);
      return;
    }

    // epoch 증가(새 세그먼트 시작)
    captureEpochRef.current += 1;
    
    // ✅ 수정
    isCapturingRef.current = true;  // 먼저 ref 올림(루프가 즉시 보고 인식)
    setIsCapturing(true);           // UI 표시
    setFrameBuffer([]);             // UI 상태 초기화
    frameBufferRef.current = [];    // 실데이터 초기화
    
    captureStartTimeRef.current = performance.now();
    frameCountRef.current = 0;
    expectedFramesRef.current = calculateExpectedFrames(startTime, endTime, now);
    encodingRef.current = false;

  //   console.log('[startCapture] ✅ START', {
  //   epoch: captureEpochRef.current,
  //   expectedFrames: expectedFramesRef.current,
  //   canvasSize: { w: canvas.width, h: canvas.height },
  // });
    console.log(`📹 캡처 시작 (예상 프레임: ${expectedFramesRef.current})`);

    /**
     * requestAnimationFrame 기반 캡처 루프
     */
    const captureFrame = () => {
      // ✅ 수정
      if (!isCapturingRef.current) return;

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
        const epochAtSchedule = captureEpochRef.current;
        // Blob으로 변환
        canvas.toBlob(
          (blob) => {
            // @ts-ignore: suppress 'myEpoch' unused diagnostic
              const myEpoch = captureEpochRef.current; // 콜백 시점의 epoch 참조(로그용)
            if (!blob) {
              encodingRef.current = false;
              return;
            }

            
            // ✅ 추가: 이미 캡처가 끝났다면 무시
            if (!isCapturingRef.current) {
              console.warn('[toBlob] late callback after stop → ignore');
              encodingRef.current = false;
              return;
            }

              // ✅ 추가: 내 epoch와 현재 epoch가 다르면 이전 세그먼트의 늦은 콜백 → 무시
            if (epochAtSchedule !== captureEpochRef.current) {
              console.warn('[toBlob] epoch mismatch → ignore', { epochAtSchedule, current: captureEpochRef.current });
              encodingRef.current = false;
              return;
            }
            const frame: Frame = {
              img: blob,
              musicTime: audio.currentTime,
              captureTime: elapsed,
            };

         
          // ✅ 수정
          frameBufferRef.current = [...frameBufferRef.current, frame]; // 실데이터 갱신
          setFrameBuffer(frameBufferRef.current);                      // UI 동기화

          //   console.log('[toBlob] +frame', {
          //   epoch: myEpoch,
          //   count: frameBufferRef.current.length,
          //   frameCountRef: frameCountRef.current,
          //   expected: expectedFramesRef.current,
          //   encodingBusy: encodingRef.current,
          // });

          if (frameBufferRef.current.length >= expectedFramesRef.current) {
           // ✅ 수정
            if (isCapturingRef.current) {
              console.log('[toBlob] reached expectedFrames → stopCapture()');
              stopCapture(); }// 한 번만 실행되도록 가드
          }

            encodingRef.current = false;
          },
          'image/jpeg',
          0.8
        );
      }

      // ✅ 수정
      if (isCapturingRef.current) {
        animationFrameIdRef.current = requestAnimationFrame(captureFrame);
      }
    };

    requestAnimationFrame(() => {
  animationFrameIdRef.current = requestAnimationFrame(captureFrame);
});
  }, [videoRef, audioRef, canvasRef,stopCapture ]);




  return {
    isCapturing,
    frameBuffer,
    startCapture,
    stopCapture,
  };
};
