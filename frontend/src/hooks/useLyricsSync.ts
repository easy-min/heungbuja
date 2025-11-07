import { useEffect, useRef, useState } from 'react';
import type { LyricLine } from '@/types';

export function useLyricsSync(
  audioRef: React.RefObject<HTMLAudioElement | null>,
  lyrics: LyricLine[],
  options?: { prerollSec?: number } // 필요시 선행 보정
) {
  const prerollSec = options?.prerollSec ?? 0; // 기본 0
  const [index, setIndex] = useState<number>(-1);
  const lastTRef = useRef<number>(0);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || lyrics.length === 0) {
      setIndex(-1);
      return;
    }

    let raf = 0;

    const tick = () => {
      raf = requestAnimationFrame(tick);

      const t = audio.currentTime - prerollSec;
      // 불필요한 계산 줄이기(아주 미세한 변화는 패스)
      // (선택) if (Math.abs(t - lastTRef.current) < 0.008) return;
      lastTRef.current = t;

      // 빠른 경로: 현재 라인이면 유지
      const cur = index >= 0 ? lyrics[index] : undefined;
      if (cur && t >= cur.start && t <= cur.end) return;

      // 대부분 앞으로 전진
      let i = Math.max(index, 0);
      while (i < lyrics.length && t > lyrics[i].end) i++;
      while (i > 0 && t < lyrics[i].start) i--;

      let nextIdx = -1;
      if (i >= 0 && i < lyrics.length && t >= lyrics[i].start && t <= lyrics[i].end) {
        nextIdx = i;
      }

      if (nextIdx !== index) setIndex(nextIdx);
    };

    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [audioRef, lyrics]); // index는 내부에서 관리

  const current = index >= 0 ? lyrics[index] : undefined;
  const next = index + 1 < lyrics.length ? lyrics[index + 1] : undefined;

  return { index, current, next };
}
