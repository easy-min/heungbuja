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
      // 1. 저장된 토큰 확인
      const savedToken = localStorage.getItem('userAccessToken');
      const savedDeviceId = localStorage.getItem('deviceId');

      // 저장된 기기번호가 현재 기기번호와 일치하는지 확인
      if (savedToken && savedDeviceId === deviceId) {
        const newToken = await refreshAccessToken();

        if (newToken) {
          console.log('토큰 갱신 성공 - 자동 로그인');
          navigate('/home', { replace: true });
          return;
        }
        console.log('토큰 갱신 실패 - 재로그인 진행');
      }

      // 2. 토큰 없거나 만료 또는 기기번호 불일치 → 기기번호로 로그인
      console.log('기기번호로 새로 로그인:', deviceId);
      const response = await deviceLoginApi({ serialNumber: deviceId });

      // 토큰 영구 저장 (localStorage)
      localStorage.setItem('userAccessToken', response.accessToken);
      localStorage.setItem('userRefreshToken', response.refreshToken);
      localStorage.setItem('userId', response.userId);
      localStorage.setItem('deviceId', deviceId); // 기기번호도 저장

      console.log('로그인 성공 - 토큰 저장 완료');

      // 메인 페이지로 이동
      navigate('/home', { replace: true });
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '인증 중 오류가 발생했습니다.';
      setError(errorMessage);
      console.error('Raspberry Pi auth error:', err);
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