// useGameWs.ts
import { useEffect, useRef, useState, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client, type IMessage } from '@stomp/stompjs';

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'http://localhost:8080/ws';

type Judgment = 1 | 2 | 3; // 1: SOSO, 2: GOOD, 3: PERFECT
interface FeedbackMessage {
  type: 'FEEDBACK';
  data: { judgment: Judgment; timestamp: number };
}

interface UseGameWsOptions {
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: Error) => void;
  onFeedback?: (msg: FeedbackMessage) => void;
}

interface UseGameWsReturn {
  isConnected: boolean;
  isConnecting: boolean;
  connect: (sessionId: string) => void;
  disconnect: () => void;
  sendFrame: (params: { sessionId: string; blob: Blob; currentPlayTime: number }) => Promise<void>;
  clientRef: React.MutableRefObject<Client | null>;
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

  /** 연결 */
  const connect = useCallback((sessionId: string) => {
    if (isConnected || isConnecting) return;

    const token = localStorage.getItem('accessToken');
    if (!token) {
      console.warn('No access token found - skip WS connection');
      return;
    }
    setIsConnecting(true);
    currentSessionRef.current = sessionId;

    try {
      const socket = new SockJS(WS_BASE_URL);

      const client = new Client({
        webSocketFactory: () => socket as unknown as WebSocket,
        connectHeaders: { Authorization: `Bearer ${token}` },
        debug: (str) => console.log('[STOMP Debug]', str),
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          setIsConnected(true);
          setIsConnecting(false);
          options?.onConnect?.();

          // 게임 채널 구독
          const dest = `/topic/game/${sessionId}`;
          client.subscribe(dest, (message: IMessage) => {
            try {
              const payload: FeedbackMessage = JSON.parse(message.body);
              if (payload?.type === 'FEEDBACK') {
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
        onWebSocketClose: () => {
          setIsConnected(false);
          setIsConnecting(false);
          options?.onError?.(new Error('WebSocket closed'));
        },
        onWebSocketError: () => {
          setIsConnected(false);
          setIsConnecting(false);
          options?.onError?.(new Error('WebSocket error'));
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
      if (!client || !client.active) return;

      const frameData = await blobToBase64(blob);
      const body = JSON.stringify({
        sessionId,
        frameData,            // Base64-encoded-image-string...
        currentPlayTime,      // 초 단위
      });

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

  /** 언마운트 시 정리 */
  useEffect(() => () => disconnect(), [disconnect]);

  return { isConnected, isConnecting, connect, disconnect, sendFrame, clientRef };
}
