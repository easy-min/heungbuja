import type { ActionPerformanceResponse } from '../../types/device';

interface ActionPerformanceProps {
  data: ActionPerformanceResponse | null;
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

  if (!data || (!data.topActions?.length && !data.weakActions?.length)) {
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

  return (
    <div className="action-performance-list">
      {/* 가장 잘하는 동작 */}
      {data.topActions && data.topActions.length > 0 && (
        <div className="action-performance-section">
          <div className="action-section-title good">✅ 가장 잘하는 동작</div>
          {data.topActions.slice(0, 1).map((action) => {
            const percentage = (action.averageScore / 3.0) * 100;
            return (
              <div key={action.actionCode} className="action-performance-item">
                <div className="action-performance-header">
                  <span className="action-icon">{actionIcons[action.actionCode] || '🤸'}</span>
                  <span className="action-name">{action.actionName}</span>
                </div>
                <div className="action-performance-stats">
                  <div className="action-stat">
                    <span className="action-stat-label">평균 점수</span>
                    <span className="action-stat-value good">
                      {action.averageScore.toFixed(2)}점
                    </span>
                  </div>
                  <div className="action-stat">
                    <span className="action-stat-label">시도 횟수</span>
                    <span className="action-stat-value">{action.attemptCount}회</span>
                  </div>
                </div>
                <div className="action-performance-bar">
                  <div
                    className="action-performance-bar-fill"
                    style={{
                      width: `${percentage}%`,
                      backgroundColor: '#10b981',
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 개선이 필요한 동작 */}
      {data.weakActions && data.weakActions.length > 0 && (
        <div className="action-performance-section">
          <div className="action-section-title weak">⚠️ 개선이 필요한 동작</div>
          {data.weakActions.slice(0, 1).map((action) => {
            const percentage = (action.averageScore / 3.0) * 100;
            return (
              <div key={action.actionCode} className="action-performance-item">
                <div className="action-performance-header">
                  <span className="action-icon">{actionIcons[action.actionCode] || '🤸'}</span>
                  <span className="action-name">{action.actionName}</span>
                </div>
                <div className="action-performance-stats">
                  <div className="action-stat">
                    <span className="action-stat-label">평균 점수</span>
                    <span className="action-stat-value weak">
                      {action.averageScore.toFixed(2)}점
                    </span>
                  </div>
                  <div className="action-stat">
                    <span className="action-stat-label">시도 횟수</span>
                    <span className="action-stat-value">{action.attemptCount}회</span>
                  </div>
                </div>
                <div className="action-performance-bar">
                  <div
                    className="action-performance-bar-fill"
                    style={{
                      width: `${percentage}%`,
                      backgroundColor: '#f59e0b',
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default ActionPerformance;
