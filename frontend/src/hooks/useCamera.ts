import { useState, useEffect, useRef } from 'react';
import { type CameraState } from '@/types';
import { CAMERA_CONFIG } from '@/utils/constants';

interface UseCameraReturn extends CameraState {
  startCamera: () => Promise<void>;
  stopCamera: () => void;
}

export const useCamera = (): UseCameraReturn => {
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const streamRef = useRef<MediaStream | null>(null);

  /**
   * 카메라 시작
   */
  const startCamera = async (): Promise<void> => {
    try {
      setError(null);
      
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: CAMERA_CONFIG.width },
          height: { ideal: CAMERA_CONFIG.height },
          frameRate: { ideal: CAMERA_CONFIG.fps, max: CAMERA_CONFIG.fps },
        },
        audio: false,
      });

      streamRef.current = mediaStream;
      setStream(mediaStream);
      setIsReady(true);
      
      console.log('✅ 카메라 시작 완료');
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '카메라 접근 실패';
      setError(errorMessage);
      setIsReady(false);
      console.error('❌ 카메라 시작 실패:', err);
    }
  };

  /**
   * 카메라 중지
   */
  const stopCamera = (): void => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
      setStream(null);
      setIsReady(false);
      console.log('⏹ 카메라 중지');
    }
  };

  /**
   * 컴포넌트 언마운트 시 카메라 정리
   */
  useEffect(() => {
    return () => {
      stopCamera();
    };
  }, []);

  return {
    stream,
    isReady,
    error,
    startCamera,
    stopCamera,
  };
};