import type { Device } from '../../types/device';
import type { User } from '../../types/user';

interface DeviceUserCardProps {
  device: Device;
  user?: User;
  hasEmergency?: boolean;
  onClickUser?: (device: Device, user?: User) => void;
}

const DeviceUserCard = ({ device, user, hasEmergency = false, onClickUser }: DeviceUserCardProps) => {

  const handleClick = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (user && onClickUser) {
      onClickUser(device, user);
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
          <div className="du-user-section" onClick={handleClick}>
            <div className="du-user-main">
              <div className="du-user-info-left">
                <div className="du-user-avatar">👤</div>
                <div className="du-user-details">
                  <h4>{user.name}</h4>
                  <p>{user.birthDate || '생년월일 정보 없음'}</p>
                </div>
              </div>
              {/* 드롭다운 아이콘은 모달 표시용으로 바꾸거나 제거 */}
              <div className="du-toggle-icon">🔍</div>
            </div>
          </div>
        ) : (
          <div className="du-empty">
            <p>연결된 사용자가 없습니다</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default DeviceUserCard;
