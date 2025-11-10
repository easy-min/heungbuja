// 기기 정보
export interface Device {
  id: number;
  serialNumber: string;
  location?: string;
  isConnected: boolean;
  connectedUserId?: number;
  createdAt: string;
}

// 기기 등록 요청
export interface RegisterDeviceRequest {
  serialNumber: string;
  location?: string;
}

// 기기 등록 응답
export interface RegisterDeviceResponse {
  id: number;
  serialNumber: string;
  location?: string;
  createdAt: string;
}