import type { ActivityItem } from '../types/activity';
import '../styles/activity-feed.css';

interface ActivityFeedProps {
  activities: ActivityItem[];
}

const ActivityFeed = ({ activities }: ActivityFeedProps) => {
  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = Math.floor((now.getTime() - date.getTime()) / 1000); // 초 단위

    if (diff < 60) return '방금 전';
    if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`;
    
    return date.toLocaleString('ko-KR', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (activities.length === 0) {
    return (
      <div className="activity-feed">
        <div className="empty-state">
          <div className="empty-icon">📝</div>
          <p>활동 내역이 없습니다</p>
        </div>
      </div>
    );
  }

  return (
    <div className="activity-feed">
      {activities.map((activity) => (
        <div
          key={activity.id}
          className={`activity-item ${activity.type.toLowerCase()}`}
        >
          <div className="activity-content">
            <div className="activity-message">{activity.message}</div>
            {activity.detail && (
              <div className="activity-detail">{activity.detail}</div>
            )}
          </div>
          <div className="activity-time">{formatTime(activity.timestamp)}</div>
        </div>
      ))}
    </div>
  );
};

export default ActivityFeed;