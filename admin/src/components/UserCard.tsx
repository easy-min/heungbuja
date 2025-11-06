import type { User } from '../types/user';
import Badge from './Badge';
import '../styles/user-card.css';

interface UserCardProps {
  user: User;
}

const UserCard = ({ user }: UserCardProps) => {
  const getStatusIcon = () => {
    switch (user.status) {
      case 'ACTIVE':
        return '✓';
      case 'WARNING':
        return '⚠️';
      case 'EMERGENCY':
        return '🚨';
      default:
        return '👤';
    }
  };

  const formatDate = (dateString?: string) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className={`user-card status-${user.status.toLowerCase()}`}>
      <div className="user-header">
        <span className="user-status-icon">{getStatusIcon()}</span>
        <div className="user-info">
          <div className="user-name">{user.name}</div>
          {user.location && <div className="user-room">{user.location}</div>}
        </div>
        <Badge status={user.status} />
      </div>

      {user.lastActivity && (
        <div className="user-activity">
          <div className="activity-type">
            {user.activityType || '최근 활동'}
          </div>
          <div className="activity-detail">
            {user.activityDetail || formatDate(user.lastActivity)}
          </div>
        </div>
      )}

      <div className="user-details">
        {user.birthDate && (
          <div className="detail-row">
            <span className="detail-label">생년월일:</span>
            <span className="detail-value">{user.birthDate}</span>
          </div>
        )}
        
        {user.emergencyContact && (
          <div className="detail-row">
            <span className="detail-label">연락처:</span>
            <span className="detail-value">{user.emergencyContact}</span>
          </div>
        )}

        {user.medicalNotes && (
          <div className="detail-row medical-notes">
            <span className="detail-label">특이사항:</span>
            <span className="detail-value">{user.medicalNotes}</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default UserCard;