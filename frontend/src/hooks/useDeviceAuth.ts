import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { deviceLoginApi } from '../api/device-auth';
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

  return {
    login,
    logout,
    isAuthenticated,
    isLoading,
    error,
  };
};