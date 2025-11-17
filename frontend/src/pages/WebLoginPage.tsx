import { useState, type FormEvent } from 'react';
import { useDeviceAuth } from '../hooks/useDeviceAuth';
import './WebLoginPage.css';

const WebLoginPage = () => {
  const [deviceNumber, setDeviceNumber] = useState('DEVICE001');
  const [userId] = useState('1');
  const { login, isLoading, error } = useDeviceAuth();

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    // 유효성 검사
    if (!deviceNumber.trim() || !userId.trim()) {
      return;
    }

    const userIdNumber = parseInt(userId);
    if (isNaN(userIdNumber) || userIdNumber <= 0) {
      return;
    }

    await login({ 
      serialNumber: deviceNumber.trim(), 
    });
  };

  return (
    <div className="user-login-container">
      <div className="user-login-section">
        <div className="user-login-header">
          <h1>🎵 흥부자</h1>
        </div>

        <div className="device-icon">📱</div>
        <div className="user-login-title">UserTest전용 기기 로그인</div>

        <div className="user-login-info">
          <strong>⚠️ 안내</strong>
          - 기기 일련번호로 로그인합니다<br/>
          - 테스트용: DEVICE001
        </div>

        <form onSubmit={handleSubmit}>
          <div className="input-wrapper">
            <label className="input-label">기기 일련번호</label>
            <input
              type="text"
              className="login-input"
              placeholder="예: DEVICE001"
              value={deviceNumber}
              onChange={(e) => setDeviceNumber(e.target.value)}
              disabled={isLoading}
            />
          </div>

          <button
            type="submit"
            className="login-btn"
            disabled={isLoading || !deviceNumber.trim() || !userId.trim()}
          >
            {isLoading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        {error && <div className="error-box">{error}</div>}
      </div>
    </div>
  );
};

export default WebLoginPage;
