import { useEffect, useRef } from 'react';

export const useWs = (url: string) => {
  const wsRef = useRef<WebSocket | null>(null);
  useEffect(() => {
    const ws = new WebSocket(url);
    ws.binaryType = 'blob';
    wsRef.current = ws;
    return () => { try { ws.close(); } catch {} wsRef.current = null; };
  }, [url]);

  const send = (blob: Blob, meta: Record<string, any>) => {
    const ws = wsRef.current; if (!ws || ws.readyState !== WebSocket.OPEN) return;
    // backpressure 간단 보호
    if (ws.bufferedAmount > 10_000_000) return; // 10MB 넘으면 잠깐 드랍
    const header = JSON.stringify({ type: 'frame', ...meta });
    const headerBuf = new TextEncoder().encode(header);
    // [길이4바이트 | header | blob] 같이 보낼 수도 있지만,
    // 여기선 편의상 두 번 send(서버에서 바로 다음 blob을 같은 세그먼트로 묶어 처리)
    ws.send(headerBuf);
    ws.send(blob);
  };

  return { wsRef, send };
};
