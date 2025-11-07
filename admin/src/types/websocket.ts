import { type EmergencyReport } from './emergency';
import { type User } from './user';

// WebSocket 메시지 타입
export type WebSocketMessageType = 
  | 'EMERGENCY_REPORT'
  | 'USER_STATUS_UPDATE'
  | 'EMERGENCY_RESOLVED'
  | 'DEVICE_CONNECTED'
  | 'DEVICE_DISCONNECTED';

// WebSocket 기본 메시지
export interface WebSocketMessage {
  type: WebSocketMessageType;
  timestamp: string;
  data: unknown;
}

// 신고 발생 메시지
export interface EmergencyReportMessage extends WebSocketMessage {
  type: 'EMERGENCY_REPORT';
  data: EmergencyReport;
}

// 어르신 상태 업데이트 메시지
export interface UserStatusUpdateMessage extends WebSocketMessage {
  type: 'USER_STATUS_UPDATE';
  data: User;
}

// 신고 해결 메시지
export interface EmergencyResolvedMessage extends WebSocketMessage {
  type: 'EMERGENCY_RESOLVED';
  data: {
    reportId: number;
    resolvedAt: string;
  };
}