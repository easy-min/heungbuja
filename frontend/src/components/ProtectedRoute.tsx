import { Navigate } from 'react-router-dom';
import { useEffect, useRef } from 'react';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute = ({ children }: ProtectedRouteProps) => {
  const isAuthenticated = !!localStorage.getItem('userAccessToken');
  const hasShownAlert = useRef(false);

  useEffect(() => {
    if (!isAuthenticated && !hasShownAlert.current) {
      hasShownAlert.current = true;
      alert('로그인해주세요!');
    }
  }, [isAuthenticated]);

  if (!isAuthenticated) {
    // 로그인되지 않았으면 로그인 페이지로 리다이렉트
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
