// 어르신 상태 타입
export type UserStatus = 'ACTIVE' | 'WARNING' | 'EMERGENCY';

// 성별 타입
export type Gender = 'MALE' | 'FEMALE' | 'OTHER';

// 어르신 정보
export interface User {
  id: number;
  name: string;
  birthDate?: string;
  gender?: Gender;
  emergencyContact?: string;
  medicalNotes?: string;
  deviceId: number;
  location?: string;
  status: UserStatus;
  lastActivity?: string;
  activityType?: string;
  activityDetail?: string;
  createdAt: string;
}

// 어르신 등록 요청
export interface RegisterUserRequest {
  name: string;
  birthDate?: string;
  gender?: Gender;
  emergencyContact?: string;
  medicalNotes?: string;
  deviceId: number;
}

// 어르신 등록 응답
export interface RegisterUserResponse {
  id: number;
  name: string;
  deviceId: number;
  createdAt: string;
}