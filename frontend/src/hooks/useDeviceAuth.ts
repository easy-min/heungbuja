import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { deviceLoginApi, refreshAccessToken } from '../api/deviceAuth';
import type { DeviceLoginRequest } from '../types/device-auth';

const useMockData = import.meta.env.VITE_USE_MOCK === 'true';

export const useDeviceAuth = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string>('');
  const navigate = useNavigate();

  const login = async (credentials: DeviceLoginRequest) => {
    setIsLoading(true);
    setError('');

    try {
      if (useMockData) {
        // Mock 모드: 자동 로그인
        await new Promise(resolve => setTimeout(resolve, 500));
        localStorage.setItem('userAccessToken', 'mock-user-token-12345');
        localStorage.setItem('userId','1');
        navigate('/voice');
      } else {
        const response = await deviceLoginApi(credentials);

        // 토큰 저장
        localStorage.setItem('userAccessToken', response.accessToken);
        localStorage.setItem('userRefreshToken', response.refreshToken);
        localStorage.setItem('userId', response.userId);

        // 홈 페이지로 이동
        navigate('/home');
      }
    } catch (err) {
      // 403 에러 처리
      if (err instanceof Error && 'status' in err && (err as Error & { status?: number }).status === 403) {
        setError('접근이 거부되었습니다. 기기가 등록되지 않았거나 권한이 없습니다.');
        return;
      }

      const errorMessage = err instanceof Error ? err.message : '로그인 중 오류가 발생했습니다.';
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    localStorage.removeItem('userAccessToken');
    localStorage.removeItem('userRefreshToken');
    localStorage.removeItem('userId');
    navigate('/user-login');
  };

  const isAuthenticated = () => {
    return !!localStorage.getItem('userAccessToken');
  };

  const autoLogin = async (deviceId: string) => {
    setIsLoading(true);
    setError('');

    try {
      // B안: local-server에서 토큰 가져오기
      console.log('🔗 local-server에서 토큰 가져오는 중...');

      const response = await fetch('http://localhost:3001/api/frontend-token');

      if (!response.ok) {
        throw new Error('local-server에서 토큰을 가져올 수 없습니다. 서버 상태를 확인하세요.');
      }

      const data = await response.json();

      // 토큰 영구 저장 (localStorage)
      localStorage.setItem('userAccessToken', data.accessToken);
      localStorage.setItem('userRefreshToken', data.refreshToken);
      localStorage.setItem('deviceId', deviceId); // 기기번호도 저장

      console.log('✅ local-server에서 토큰 받아옴 - 저장 완료');
      console.log('📅 토큰 만료 시간:', new Date(data.expiryTime).toLocaleString());

      // 메인 페이지로 이동
      navigate('/home', { replace: true });
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '인증 중 오류가 발생했습니다.';
      setError(errorMessage);
      console.error('❌ local-server 토큰 가져오기 실패:', err);
    } finally {
      setIsLoading(false);
    }
  };

  return {
    login,
    logout,
    isAuthenticated,
    autoLogin,
    isLoading,
    error,
  };
};