import { useNotificationStore } from '../stores';
import '../styles/dashboard-header.css';

interface DashboardHeaderProps {
  onNotificationClick: () => void;
}

const DashboardHeader = ({ onNotificationClick }: DashboardHeaderProps) => {
  const unreadCount = useNotificationStore((state) => state.unreadCount);
  const showBadge = useNotificationStore((state) => state.showBadge);

  return (
    <div className="dashboard-header">
      <div className="header-content">
        <h1>🏥 관리자 페이지</h1>
        <div 
          className="notification-icon" 
          onClick={onNotificationClick}
        >
          🔔
          {showBadge && unreadCount > 0 && (
            <span className="notification-badge">{unreadCount}</span>
          )}
        </div>
      </div>
    </div>
  );
};

export default DashboardHeader;