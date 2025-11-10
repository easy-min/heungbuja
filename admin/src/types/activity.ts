// 활동 타입
export type ActivityType = 'EMERGENCY' | 'WARNING' | 'INFO';

// 활동 피드 아이템
export interface ActivityItem {
  id: string;
  type: ActivityType;
  message: string;
  detail?: string;
  userId?: number;
  userName?: string;
  timestamp: string;
}