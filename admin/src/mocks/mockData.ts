import { type EmergencyReport } from '../types/emergency';
import { type User } from '../types/user';
import { type Device } from '../types/device';

// Mock 신고 데이터
export const mockEmergencyReports: EmergencyReport[] = [
  {
    reportId: 1,
    userId: 1,
    userName: '김영희',
    status: 'CONFIRMED',
    reportedAt: new Date(Date.now() - 1000 * 60 * 10).toISOString(), // 10분 전
    triggerWord: '도와주세요',
    isConfirmed: true,
    message: '낙상 사고 발생',
  },
  {
    reportId: 2,
    userId: 2,
    userName: '이철수',
    status: 'CONFIRMED',
    reportedAt: new Date(Date.now() - 1000 * 60 * 30).toISOString(), // 30분 전
    triggerWord: '살려줘',
    isConfirmed: true,
    message: '심장 통증 호소',
  },
  {
    reportId: 3,
    userId: 3,
    userName: '박순자',
    status: 'RESOLVED',
    reportedAt: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(), // 2시간 전
    triggerWord: '아파요',
    isConfirmed: true,
    message: '어지러움 증상',
  },
];

// Mock 어르신 데이터
export const mockUsers: User[] = [
  {
    id: 1,
    name: '김영희',
    birthDate: '1945-03-15',
    gender: 'FEMALE',
    emergencyContact: '010-1234-5678',
    medicalNotes: '고혈압 약 복용 중',
    deviceId: 1,
    location: '101호',
    status: 'WARNING',
    lastActivity: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
    activityType: '이동 감지',
    activityDetail: '화장실로 이동',
    createdAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 2,
    name: '이철수',
    birthDate: '1940-07-20',
    gender: 'MALE',
    emergencyContact: '010-2345-6789',
    medicalNotes: '당뇨 관리 필요',
    deviceId: 2,
    location: '102호',
    status: 'EMERGENCY',
    lastActivity: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    activityType: '긴급 신호',
    activityDetail: '심장 통증',
    createdAt: '2024-01-02T00:00:00Z',
  },
  {
    id: 3,
    name: '박순자',
    birthDate: '1950-11-10',
    gender: 'FEMALE',
    emergencyContact: '010-3456-7890',
    deviceId: 3,
    location: '103호',
    status: 'ACTIVE',
    lastActivity: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
    activityType: '정상 활동',
    activityDetail: '식사 중',
    createdAt: '2024-01-03T00:00:00Z',
  },
  {
    id: 4,
    name: '최민수',
    birthDate: '1948-05-25',
    gender: 'MALE',
    emergencyContact: '010-4567-8901',
    medicalNotes: '알레르기: 페니실린',
    deviceId: 4,
    location: '104호',
    status: 'ACTIVE',
    lastActivity: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
    activityType: '휴식 중',
    activityDetail: '침대에서 휴식',
    createdAt: '2024-01-04T00:00:00Z',
  },
];

// Mock 기기 데이터
export const mockDevices: Device[] = [
  {
    id: 1,
    serialNumber: 'DEVICE-2024-001',
    location: '101호',
    isConnected: true,
    connectedUserId: 1,
    createdAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 2,
    serialNumber: 'DEVICE-2024-002',
    location: '102호',
    isConnected: true,
    connectedUserId: 2,
    createdAt: '2024-01-02T00:00:00Z',
  },
  {
    id: 3,
    serialNumber: 'DEVICE-2024-003',
    location: '103호',
    isConnected: true,
    connectedUserId: 3,
    createdAt: '2024-01-03T00:00:00Z',
  },
  {
    id: 4,
    serialNumber: 'DEVICE-2024-004',
    location: '104호',
    isConnected: true,
    connectedUserId: 4,
    createdAt: '2024-01-04T00:00:00Z',
  },
  {
    id: 5,
    serialNumber: 'DEVICE-2024-005',
    location: '105호',
    isConnected: false,
    createdAt: '2024-01-05T00:00:00Z',
  },
];