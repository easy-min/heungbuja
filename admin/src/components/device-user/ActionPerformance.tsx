import type { ActionPerformance as ActionPerformanceType } from '../../types/device';

interface ActionPerformanceProps {
  data: ActionPerformanceType[];
  isLoading: boolean;
}

const ActionPerformance = ({ data, isLoading }: ActionPerformanceProps) => {
  if (isLoading) {
    return (
      <div className="du-loading">
        <p>수행도 데이터를 불러오는 중...</p>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="du-empty">
        <p>수행도 데이터가 없습니다</p>
      </div>
    );
  }

  const actionIcons: Record<number, string> = {
    0: '👏',
    1: '👐',
    2: '🍑',
    3: '🙆‍♀️',
    4: '🤸',
    5: '🚪',
    6: '🙋',
    7: '💃',
  };

  const getAccuracyColor = (accuracy: number) => {
    if (accuracy >= 80) return '#10b981';
    if (accuracy >= 60) return '#f59e0b';
    return '#ef4444';
  };

  return (
    <div className="action-performance-list">
      {data.map((action) => (
        <div key={action.actionCode} className="action-performance-item">
          <div className="action-performance-header">
            <span className="action-icon">{actionIcons[action.actionCode] || '🤸'}</span>
            <span className="action-name">{action.actionName}</span>
          </div>
          <div className="action-performance-stats">
            <div className="action-stat">
              <span className="action-stat-label">성공</span>
              <span className="action-stat-value">{action.successCount}회</span>
            </div>
            <div className="action-stat">
              <span className="action-stat-label">전체</span>
              <span className="action-stat-value">{action.totalCount}회</span>
            </div>
            <div className="action-stat">
              <span className="action-stat-label">정확도</span>
              <span
                className="action-stat-value"
                style={{ color: getAccuracyColor(action.accuracy) }}
              >
                {action.accuracy.toFixed(1)}%
              </span>
            </div>
          </div>
          <div className="action-performance-bar">
            <div
              className="action-performance-bar-fill"
              style={{
                width: `${action.accuracy}%`,
                backgroundColor: getAccuracyColor(action.accuracy),
              }}
            />
          </div>
        </div>
      ))}
    </div>
  );
};

export default ActionPerformance;
