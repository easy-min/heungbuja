import { useEffect, useRef, useState } from 'react';
import type { LyricLine } from '@/types';

export function useLyricsSync(
  audioRef: React.RefObject<HTMLAudioElement | null>,
  lyrics: LyricLine[],
  options?: { prerollSec?: number }
) {
  const prerollSec = options?.prerollSec ?? 0;
  const [index, setIndex] = useState<number>(-1);
  const [isInstrumental, setIsInstrumental] = useState<boolean>(true);
  const lastTRef = useRef<number>(0);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || lyrics.length === 0) {
      setIndex(-1);
      setIsInstrumental(true);
      return;
    }

    let raf = 0;

    const tick = () => {
      raf = requestAnimationFrame(tick);

      const t = audio.currentTime - prerollSec;
      lastTRef.current = t;

      const cur = index >= 0 ? lyrics[index] : undefined;
      if (cur && t >= cur.start && t <= cur.end) {
        if (isInstrumental) setIsInstrumental(false);
        return;
      }

      // 다음 라인 탐색
      let i = Math.max(index, 0);
      while (i < lyrics.length && t > lyrics[i].end) i++;
      while (i > 0 && t < lyrics[i].start) i--;

      let nextIdx = -1;
      if (i >= 0 && i < lyrics.length && t >= lyrics[i].start && t <= lyrics[i].end) {
        nextIdx = i;
      }

      // 변경 감지
      if (nextIdx !== index) {
        setIndex(nextIdx);
        setIsInstrumental(nextIdx === -1);
      }
    };

    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [audioRef, lyrics, index, isInstrumental, prerollSec]);

  const current = index >= 0 ? lyrics[index] : undefined;
  const next = index + 1 < lyrics.length ? lyrics[index + 1] : undefined;

  return { index, current, next, isInstrumental };
}
