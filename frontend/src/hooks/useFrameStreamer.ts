import { useRef, useState, useCallback } from 'react';
import { GAME_CONFIG } from '@/utils/constants';

type Sender = (blob: Blob, meta: { t: number; idx: number }) => void;

export const useFrameStreamer = ({
  videoRef, audioRef, canvasRef,
}: {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  audioRef: React.RefObject<HTMLAudioElement | null>;
  canvasRef: React.RefObject<HTMLCanvasElement | null>;
}) => {
  const [isCapturing, setIsCapturing] = useState(false);
  const isRunningRef = useRef(false);
  const rafRef = useRef<number | null>(null);
  const lastTickRef = useRef<number>(0);
  const frameIdxRef = useRef<number>(0);

  const stop = useCallback(() => {
    isRunningRef.current = false;
    setIsCapturing(false);
    if (rafRef.current !== null) { cancelAnimationFrame(rafRef.current); rafRef.current = null; }
  }, []);

  const start = useCallback((startTime: number, endTime: number, send: Sender) => {
    const v = videoRef.current, a = audioRef.current, c = canvasRef.current;
    if (!v || !a || !c) return;
    const ctx = c.getContext('2d'); if (!ctx) return;

    // 너무 늦게 시작하려 하면 패스
    if (a.currentTime > endTime - 0.03) { return; }

    isRunningRef.current = true;
    setIsCapturing(true);
    frameIdxRef.current = 0;
    lastTickRef.current = performance.now();

    const step = () => {
      if (!isRunningRef.current) return;
      const nowAudio = a.currentTime;
      if (nowAudio >= endTime) { stop(); return; }

      const now = performance.now();
      const shouldCapture = (now - lastTickRef.current) >= GAME_CONFIG.FRAME_MS - 1; // 약간 여유
      if (shouldCapture) {
        lastTickRef.current += GAME_CONFIG.FRAME_MS;

        ctx.drawImage(v, 0, 0, c.width, c.height);
        const idx = frameIdxRef.current++;

        c.toBlob((blob) => {
          if (!blob || !isRunningRef.current) return;

          send(blob, { t: nowAudio, idx });
        }, 'image/jpeg', 0.8);
      }

      rafRef.current = requestAnimationFrame(step);
    };

    rafRef.current = requestAnimationFrame(step);
  }, [videoRef, audioRef, canvasRef, stop]);

  return { isCapturing, start, stop };
};