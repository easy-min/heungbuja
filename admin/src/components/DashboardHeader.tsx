import { useNotificationStore } from '../stores';
import '../styles/dashboard-header.css';

type DashboardTab = 'admin' | 'developer';

interface DashboardHeaderProps {
  activeTab: DashboardTab;
  onTabChange: (tab: DashboardTab) => void;
  onNotificationClick: () => void;
}

const DashboardHeader = ({
  activeTab,
  onTabChange,
  onNotificationClick,
}: DashboardHeaderProps) => {
  const unreadCount = useNotificationStore((state) => state.unreadCount);
  const showBadge = useNotificationStore((state) => state.showBadge);

  return (
    <div className="dashboard-header">
      <div className="header-content">
        {/* 왼쪽: 제목 + 탭 */}
        <div className="header-left">
          <h1>흥부자 대시보드</h1>
          <div className="header-tabs">
            <button
              type="button"
              className={`header-tab ${
                activeTab === 'admin' ? 'header-tab--active' : ''
              }`}
              onClick={() => onTabChange('admin')}
            >
              관리자 페이지
            </button>
            <button
              type="button"
              className={`header-tab ${
                activeTab === 'developer' ? 'header-tab--active' : ''
              }`}
              onClick={() => onTabChange('developer')}
            >
              개발자 페이지
            </button>
          </div>
        </div>

        {/* 오른쪽: 알림 아이콘 */}
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
