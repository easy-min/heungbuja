import { useEffect, useRef, useState, useCallback } from 'react';
import type { GameWsMessage } from '@/types/game';

import SockJS from 'sockjs-client';
import { Client, type IMessage } from '@stomp/stompjs';

import JSZip from 'jszip';
import { saveAs } from 'file-saver';

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'https://localhost:8080/ws';

interface UseGameWsOptions {
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: Error) => void;
  onFeedback?: (msg: GameWsMessage) => void;
}

interface UseGameWsReturn {
  isConnected: boolean;
  isConnecting: boolean;
  connect: (sessionId: string) => void;
  disconnect: () => void;
  sendFrame: (params: { sessionId: string; blob: Blob; currentPlayTime: number }) => Promise<void>;
  clientRef: React.MutableRefObject<Client | null>;
    /** 디버그용: 메모리에 쌓인 프레임 개수 */
  debugFramesCount: number;
  /** 쌓인 프레임들을 ZIP으로 묶어 한 번에 다운로드 */
  downloadFramesZip: () => Promise<void>;
}

/** Blob -> Base64 (prefix 없이 본문만 반환) */
async function blobToBase64(blob: Blob): Promise<string> {
  const buf = await blob.arrayBuffer();
  let binary = '';
  const bytes = new Uint8Array(buf);
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

export function useGameWs(options?: UseGameWsOptions): UseGameWsReturn {
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const currentSessionRef = useRef<string | null>(null);
  const everConnectedRef = useRef(false);

  // ▼ 디버그용 프레임 버퍼
  const [debugFramesCount, setDebugFramesCount] = useState(0);
  const debugFramesRef = useRef<{ blob: Blob; time: number; idx: number }[]>([]);
  const debugFrameIndexRef = useRef(0);

  /** 연결 */
  const connect = useCallback((sessionId: string) => {
    if (isConnected || isConnecting) return;

    setIsConnecting(true);
    currentSessionRef.current = sessionId;

    try {
      const socket = new SockJS(WS_BASE_URL);

      const client = new Client({
        webSocketFactory: () => socket as unknown as WebSocket,
        // debug: (str) => console.log('[STOMP Debug]', str),
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          console.log('[STOMP] connected');
          everConnectedRef.current = true;
          setIsConnected(true);
          setIsConnecting(false);
          options?.onConnect?.();

          // 게임 채널 구독
          const dest = `/topic/game/${sessionId}`;
          client.subscribe(dest, (message: IMessage) => {
            try {
              const payload = JSON.parse(message.body) as GameWsMessage;
              if (payload.type === 'FEEDBACK' || payload.type === 'LEVEL_DECISION') {
                options?.onFeedback?.(payload);
              }
            } catch (e) {
              console.error('Feedback parse error:', e);
            }
          });
        },
        onDisconnect: () => {
          setIsConnected(false);
          setIsConnecting(false);
          options?.onDisconnect?.();
        },
        onStompError: (frame) => {
          const err = new Error(frame.headers['message'] || 'STOMP error');
          setIsConnected(false);
          setIsConnecting(false);
          options?.onError?.(err);
        },
        onWebSocketClose: (evt) => {
          console.warn('[STOMP] websocket closed', evt.code, evt.reason);
          setIsConnected(false);
          setIsConnecting(false);

          if (!everConnectedRef.current) {
            options?.onError?.(new Error('WebSocket closed before first connect'));
          } else {
            options?.onDisconnect?.();
          }
        },
        onWebSocketError: () => {
          setIsConnected(false);
          setIsConnecting(false);
          if (!everConnectedRef.current) {
            options?.onError?.(new Error('WebSocket error before first connect'));
          } else {
            options?.onDisconnect?.();
          }
        },
      });

      client.activate();
      clientRef.current = client;
    } catch (e) {
      console.error('WS connect error:', e);
      setIsConnecting(false);
      options?.onError?.(e as Error);
    }
  }, [isConnected, isConnecting, options]);

  /** 해제 */
  const disconnect = useCallback(() => {
    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }
    currentSessionRef.current = null;
    setIsConnected(false);
    setIsConnecting(false);
  }, []);

  /** 프레임 전송: /app/game/frame 로 JSON 본문 전송 */
    const sendFrame = useCallback(
    async ({ sessionId, blob, currentPlayTime }: { sessionId: string; blob: Blob; currentPlayTime: number }) => {
      const client = clientRef.current;
      if (!client || !client.connected) return;

      // ---- 1) 서버 전송용 Base64 변환
      const frameData = await blobToBase64(blob);
      const body = JSON.stringify({
        sessionId,
        frameData,
        currentPlayTime, // 초 단위
      });

      // ---- 2) 디버그용: 메모리에만 프레임 쌓기 (DEV에서만)
      // if (import.meta.env.DEV) {
        const idx = debugFrameIndexRef.current++;
        debugFramesRef.current.push({ blob, time: currentPlayTime, idx });
        setDebugFramesCount(debugFramesRef.current.length);
        // 필요하다면 최대 개수 제한도 가능:
        // if (debugFramesRef.current.length > 200) debugFramesRef.current.shift();
      // }

      // ---- 3) WebSocket 전송
      try {
        client.publish({
          destination: '/app/game/frame',
          body,
          headers: { 'content-type': 'application/json' },
        });
      } catch (e) {
        console.error('sendFrame error:', e);
        options?.onError?.(e as Error);
      }
    },
    [options]
  );

  const downloadFramesZip = useCallback(async () => {
    const frames = debugFramesRef.current;
    if (!frames.length) {
      console.log('No debug frames to download.');
      return;
    }

    try {
      const zip = new JSZip();
      const folder = zip.folder('frames');

      if (!folder) throw new Error('Failed to create zip folder.');

      // Blob → ArrayBuffer로 변환해서 zip에 추가
      for (const f of frames) {
        const arrayBuf = await f.blob.arrayBuffer();
        const filename = `frame_${f.idx}_${f.time.toFixed(3)}.jpg`; // 필요한 확장자로 수정 가능
        folder.file(filename, arrayBuf);
      }

      const zipBlob = await zip.generateAsync({ type: 'blob' });
      const ts = new Date().toISOString().replace(/[:.]/g, '-');
      saveAs(zipBlob, `frames_${ts}.zip`);

      // 다운로드 후 비우기 (원하면 주석 처리)
      debugFramesRef.current = [];
      debugFrameIndexRef.current = 0;
      setDebugFramesCount(0);
    } catch (e) {
      console.error('downloadFramesZip error:', e);
      options?.onError?.(e as Error);
    }
  }, [options]);



  /** 언마운트 시 정리 */
  useEffect(() => () => disconnect(), [disconnect]);

  return { isConnected, isConnecting, connect, disconnect, sendFrame, clientRef, debugFramesCount, downloadFramesZip, };
}
