import type { ActivityLog } from '../../types/device';

interface RecentActivitiesProps {
  data: ActivityLog[];
  isLoading: boolean;
}

const RecentActivities = ({ data, isLoading }: RecentActivitiesProps) => {
  if (isLoading) {
    return (
      <div className="du-loading">
        <p>활동 로그를 불러오는 중...</p>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="du-empty">
        <p>활동 기록이 없습니다</p>
      </div>
    );
  }

  const getActivityIcon = (type: string) => {
    switch (type) {
      case 'MUSIC_PLAY':
        return '🎵';
      case 'EXERCISE_COMPLETE':
        return '💪';
      case 'EMERGENCY':
        return '🚨';
      case 'DEVICE_CONNECT':
        return '📱';
      default:
        return '📋';
    }
  };

  const getActivityColor = (type: string) => {
    switch (type) {
      case 'MUSIC_PLAY':
        return '#3182f6';
      case 'EXERCISE_COMPLETE':
        return '#10b981';
      case 'EMERGENCY':
        return '#ef4444';
      case 'DEVICE_CONNECT':
        return '#8b5cf6';
      default:
        return '#6b7280';
    }
  };

  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (minutes < 1) return '방금 전';
    if (minutes < 60) return `${minutes}분 전`;
    if (hours < 24) return `${hours}시간 전`;
    if (days < 7) return `${days}일 전`;

    return date.toLocaleDateString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="recent-activities-list">
      {data.map((activity) => (
        <div key={activity.id} className="activity-log-item">
          <div
            className="activity-log-icon"
            style={{ backgroundColor: `${getActivityColor(activity.type)}20` }}
          >
            <span style={{ color: getActivityColor(activity.type) }}>
              {getActivityIcon(activity.type)}
            </span>
          </div>
          <div className="activity-log-content">
            <div className="activity-log-description">{activity.description}</div>
            <div className="activity-log-time">{formatTimestamp(activity.timestamp)}</div>
          </div>
        </div>
      ))}
    </div>
  );
};

export default RecentActivities;
