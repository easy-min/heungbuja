import { useEffect, useRef, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client, type IMessage } from '@stomp/stompjs';
import type{
  EmergencyReportMessage,
  UserStatusUpdateMessage,
  EmergencyResolvedMessage,
} from '../types/websocket';
import { useEmergencyStore, useUserStore, useActivityStore, useNotificationStore } from '../stores';

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'http://localhost:8080/ws';

interface UseWebSocketOptions {
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: Error) => void;
}

export const useWebSocket = (options?: UseWebSocketOptions) => {
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const clientRef = useRef<Client | null>(null);
  
  // 스토어 액션들
  const addReport = useEmergencyStore((state) => state.addReport);
  const updateReport = useEmergencyStore((state) => state.updateReport);
  const updateUser = useUserStore((state) => state.updateUser);
  const addActivity = useActivityStore((state) => state.addActivity);
  const incrementUnread = useNotificationStore((state) => state.incrementUnread);

  // WebSocket 연결
  const connect = () => {
    if (isConnected || isConnecting) return;

    const token = localStorage.getItem('accessToken');
    if (!token) {
      console.warn('No access token found - skipping WebSocket connection');
      return;
    }
    const adminId = localStorage.getItem('adminId');
    if (!adminId) {
      console.warn('No adminId found - skipping WebSocket connection');
      return;
    }
    setIsConnecting(true);

    try {
      // SockJS 소켓 생성
      const socket = new SockJS(WS_BASE_URL);
      
      // STOMP 클라이언트 생성
      const client = new Client({
        webSocketFactory: () => socket as any,
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        debug: (str) => {
          console.log('[STOMP Debug]', str);
        },
        reconnectDelay: 5000, // 5초마다 재연결 시도
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          console.log('✅ WebSocket Connected');
          setIsConnected(true);
          setIsConnecting(false);
          options?.onConnect?.();

          // 신고 구독
          const emergencyPath = `/topic/admin/${adminId}/emergency`;
          client.subscribe(emergencyPath, (message: IMessage) => {
            handleEmergencyReport(message);
          });

          // 어르신 상태 업데이트 구독
          const userStatusPath = `/topic/admin/${adminId}/user-status`;
          client.subscribe(userStatusPath, (message: IMessage) => {
            handleUserStatusUpdate(message);
          });

        },
        onDisconnect: () => {
          console.log('❌ WebSocket Disconnected');
          setIsConnected(false);
          setIsConnecting(false);
          options?.onDisconnect?.();
        },
        onStompError: (frame) => {
          console.error('STOMP Error:', frame);
          setIsConnected(false);
          setIsConnecting(false);
          const error = new Error(frame.headers['message'] || 'STOMP error');
          options?.onError?.(error);
        },
      });

      client.activate();
      clientRef.current = client;
    } catch (error) {
      console.error('WebSocket connection error:', error);
      setIsConnecting(false);
      options?.onError?.(error as Error);
    }
  };

  // WebSocket 연결 해제
  const disconnect = () => {
    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
      setIsConnected(false);
    }
  };

  // 신고 메시지 처리
  const handleEmergencyReport = (message: IMessage) => {
    try {
      const data: EmergencyReportMessage = JSON.parse(message.body);
      console.log('🚨 New Emergency Report:', data);

      // 스토어에 신고 추가
      addReport(data.data);

      // 활동 피드에 추가
      addActivity({
        id: `emergency-${data.data.id}-${Date.now()}`,
        type: 'EMERGENCY',
        message: `긴급 신고 발생: ${data.data.userName}`,
        detail: data.data.location,
        userId: data.data.userId,
        userName: data.data.userName,
        timestamp: data.data.reportedAt,
      });

      // 알림 카운트 증가
      incrementUnread();

      // 브라우저 알림 (권한이 있으면)
      if (Notification.permission === 'granted') {
        new Notification('🚨 긴급 신고 발생!', {
          body: `${data.data.userName}님의 신고가 접수되었습니다.`,
          icon: '/emergency-icon.png',
        });
      }
    } catch (error) {
      console.error('Error handling emergency report:', error);
    }
  };

  // 어르신 상태 업데이트 처리
  const handleUserStatusUpdate = (message: IMessage) => {
    try {
      const data: UserStatusUpdateMessage = JSON.parse(message.body);
      console.log('👤 User Status Update:', data);

      // 스토어에서 어르신 상태 업데이트
      updateUser(data.data.id, data.data);

      // 활동 피드에 추가 (긴급/경고 상태일 때만)
      if (data.data.status === 'EMERGENCY' || data.data.status === 'WARNING') {
        addActivity({
          id: `user-status-${data.data.id}-${Date.now()}`,
          type: data.data.status === 'EMERGENCY' ? 'EMERGENCY' : 'WARNING',
          message: `${data.data.name}님 상태 변경: ${data.data.status}`,
          detail: data.data.activityDetail,
          userId: data.data.id,
          userName: data.data.name,
          timestamp: data.timestamp,
        });
      }
    } catch (error) {
      console.error('Error handling user status update:', error);
    }
  };

  // 신고 해결 처리
  const handleEmergencyResolved = (message: IMessage) => {
    try {
      const data: EmergencyResolvedMessage = JSON.parse(message.body);
      console.log('✅ Emergency Resolved:', data);

      // 스토어에서 신고 상태 업데이트
      updateReport(data.data.reportId, {
        status: 'RESOLVED',
        resolvedAt: data.data.resolvedAt,
      });

      // 활동 피드에 추가
      addActivity({
        id: `resolved-${data.data.reportId}-${Date.now()}`,
        type: 'INFO',
        message: `신고 #${data.data.reportId} 처리 완료`,
        timestamp: data.data.resolvedAt,
      });
    } catch (error) {
      console.error('Error handling emergency resolved:', error);
    }
  };

  // 브라우저 알림 권한 요청
  const requestNotificationPermission = async () => {
    if ('Notification' in window && Notification.permission === 'default') {
      await Notification.requestPermission();
    }
  };

  // 컴포넌트 마운트 시 연결하지 않음 (명시적으로 호출 필요)
  useEffect(() => {
    requestNotificationPermission();

    return () => {
      disconnect();
    };
  }, []);

  return {
    isConnected,
    isConnecting,
    connect,
    disconnect,
  };
};