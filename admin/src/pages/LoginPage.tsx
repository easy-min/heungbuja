import { useState,type FormEvent } from 'react';
import { useAuth } from '../hooks/useAuth';
import '../styles/login.css';

const LoginPage = () => {
  const [username, setUsername] = useState('superadmin');
  const [password, setPassword] = useState('superadmin123!');
  const { login, isLoading, error } = useAuth();

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    // 유효성 검사
    if (!username.trim() || !password.trim()) {
      return;
    }

    await login({ username, password });
  };

  return (
    <div className="login-container">
      <div className="login-section">
        <div className="login-header">
          <h1>🎵 흥부자</h1>
        </div>

        <div className="login-title">관리자 로그인</div>

        <form onSubmit={handleSubmit}>
          <input
            type="text"
            className="login-input"
            placeholder="아이디"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            disabled={isLoading}
          />

          <input
            type="password"
            className="login-input"
            placeholder="비밀번호"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={isLoading}
          />

          <button
            type="submit"
            className="login-btn"
            disabled={isLoading || !username.trim() || !password.trim()}
          >
            {isLoading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        {error && <div className="error-box">{error}</div>}
      </div>
    </div>
  );
};

export default LoginPage;