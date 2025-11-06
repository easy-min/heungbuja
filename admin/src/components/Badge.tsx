import '../styles/badge.css';
import type { EmergencyStatus } from '../types/emergency';
import type { UserStatus } from '../types/user';

interface BadgeProps {
  status: EmergencyStatus | UserStatus;
  text?: string;
}

const Badge = ({ status, text }: BadgeProps) => {
  const getStatusText = () => {
    if (text) return text;
    
    const statusMap: Record<string, string> = {
      PENDING: '대기',
      CONFIRMED: '확인됨',
      RESOLVED: '해결됨',
      FALSE_ALARM: '오신고',
      ACTIVE: '정상',
      WARNING: '주의',
      EMERGENCY: '긴급',
    };
    
    return statusMap[status] || status;
  };

  const getStatusClass = () => {
    const classMap: Record<string, string> = {
      PENDING: 'badge-warning',
      CONFIRMED: 'badge-danger',
      RESOLVED: 'badge-success',
      FALSE_ALARM: 'badge-secondary',
      ACTIVE: 'badge-success',
      WARNING: 'badge-warning',
      EMERGENCY: 'badge-danger',
    };
    
    return classMap[status] || 'badge-secondary';
  };

  return (
    <span className={`badge ${getStatusClass()}`}>
      {getStatusText()}
    </span>
  );
};

export default Badge;