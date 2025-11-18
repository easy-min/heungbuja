import { useState } from 'react';
import type { Device } from '../../types/device';
import type { User } from '../../types/user';
import UserDetailsPanel from './UserDetailsPanel';

interface DeviceUserCardProps {
  device: Device;
  user?: User;
  hasEmergency?: boolean;
}

const DeviceUserCard = ({ device, user, hasEmergency = false }: DeviceUserCardProps) => {
  const [isOpen, setIsOpen] = useState(false);

  const togglePanel = () => {
    if (user) {
      setIsOpen(!isOpen);
    }
  };

  return (
    <div className="device-user-card">
      {/* 카드 헤더 */}
      <div className="du-card-header">
        <div className="du-device-info">
          <div className="du-device-icon">📱</div>
          <div className="du-device-details">
            <h3>기기 #{device.id}</h3>
            <p>{device.serialNumber}</p>
          </div>
        </div>
        <div className={`du-emergency-siren ${hasEmergency ? 'active' : ''}`}>🚨</div>
      </div>

      {/* 카드 본문 */}
      <div className="du-card-body">
        {user ? (
          <>
            {/* 사용자 기본 정보 (클릭하면 펼침) */}
            <div className="du-user-section" onClick={togglePanel}>
              <div className="du-user-main">
                <div className="du-user-info-left">
                  <div className="du-user-avatar">👤</div>
                  <div className="du-user-details">
                    <h4>{user.name}</h4>
                    <p>{user.birthDate || '생년월일 정보 없음'}</p>
                  </div>
                </div>
                <div className={`du-toggle-icon ${isOpen ? 'open' : ''}`}>▼</div>
              </div>
            </div>

            {/* 상세 패널 */}
            <UserDetailsPanel userId={user.id} isOpen={isOpen} />
          </>
        ) : (
          <div className="du-empty">
            <div className="icon">👤</div>
            <p>연결된 사용자가 없습니다</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default DeviceUserCard;
