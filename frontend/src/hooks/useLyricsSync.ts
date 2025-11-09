import { useEffect, useRef, useState } from 'react';
import type { LyricLine } from '@/types/song';

export function useLyricsSync(
  audioRef: React.RefObject<HTMLAudioElement | null>,
  lyrics: LyricLine[],
  options?: { prerollSec?: number }
) {
  const prerollSec = options?.prerollSec ?? 0;

  const [index, setIndex] = useState<number>(-1);
  const [isInstrumental, setIsInstrumental] = useState<boolean>(true);

  const indexRef = useRef(index);
  const isInstrumentalRef = useRef(isInstrumental);

  useEffect(() => {
    indexRef.current = index;
  }, [index]);

  useEffect(() => {
    isInstrumentalRef.current = isInstrumental;
  }, [isInstrumental]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || lyrics.length === 0) {
      setIndex(-1);
      setIsInstrumental(true);
      return;
    }

    let rafId = 0;

    const tick = () => {
      rafId = requestAnimationFrame(tick);

      const t = audio.currentTime - prerollSec;
      const curIdx = indexRef.current;
      const cur = curIdx >= 0 ? lyrics[curIdx] : undefined;

      if (cur && t >= cur.startTime && t <= cur.endTime) {
        if (isInstrumentalRef.current) setIsInstrumental(false);
        return;
      }

      // 다음 라인 탐색
      let nextIdx = -1;
      for (let i = 0; i < lyrics.length; i++) {
        const l = lyrics[i];
        if (t >= l.startTime && t <= l.endTime) {
          nextIdx = i;
          break;
        }
      }

      // 상태 업데이트
      if (nextIdx !== curIdx) {
        setIndex(nextIdx);
        setIsInstrumental(nextIdx === -1);
      }
    };

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [audioRef, lyrics, prerollSec]);

  const current = index >= 0 ? lyrics[index] : undefined;
  const next = index + 1 < lyrics.length ? lyrics[index + 1] : undefined;

  return { index, current, next, isInstrumental };
}
