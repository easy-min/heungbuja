// 사용자(기기) 로그인 요청 타입
export interface DeviceLoginRequest {
  serialNumber: string;
}

// 사용자(기기) 로그인 응답 타입
export interface DeviceLoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  userId: string;
  role: string;
}