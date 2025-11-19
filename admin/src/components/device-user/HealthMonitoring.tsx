import type { GameStats } from '../../types/device';

interface HealthMonitoringProps {
  data: GameStats | null;
  isLoading: boolean;
}

const HealthMonitoring = ({ data, isLoading }: HealthMonitoringProps) => {
  if (isLoading) {
    return (
      <div className="du-loading">
        <p>게임 통계를 불러오는 중...</p>
      </div>
    );
  }

  if (!data || data.totalGames === 0) {
    return (
      <div className="du-empty">
        <p>아직 게임 기록이 없습니다</p>
      </div>
    );
  }

  const getScoreColor = (score: number) => {
    if (score >= 2.5) return '#10b981'; // 초록색 (좋음)
    if (score >= 2.0) return '#f59e0b'; // 주황색 (보통)
    return '#ef4444'; // 빨간색 (나쁨)
  };

  const completionRate = data.totalGames > 0
    ? ((data.completedGames / data.totalGames) * 100).toFixed(1)
    : '0';

  return (
    <>
      <div className="health-stats-grid">
        <div className="health-stat-card">
          <div className="health-stat-icon">🎮</div>
          <div className="health-stat-info">
            <div className="health-stat-label">총 게임 수</div>
            <div className="health-stat-value">
              {data.totalGames} <span className="health-stat-unit">회</span>
            </div>
          </div>
        </div>

        <div className="health-stat-card">
          <div className="health-stat-icon">⭐</div>
          <div className="health-stat-info">
            <div className="health-stat-label">평균 점수</div>
            <div className="health-stat-value">
              <span style={{ color: getScoreColor(data.overallAverageScore) }}>
                {data.overallAverageScore.toFixed(2)}
              </span>
            </div>
          </div>
        </div>

        <div className="health-stat-card">
          <div className="health-stat-icon">💯</div>
          <div className="health-stat-info">
            <div className="health-stat-label">PERFECT 비율</div>
            <div className="health-stat-value">
              <span style={{ color: '#10b981' }}>
                {data.perfectRate.toFixed(1)}
              </span>
              <span className="health-stat-unit">%</span>
            </div>
          </div>
        </div>

        <div className="health-stat-card">
          <div className="health-stat-icon">✅</div>
          <div className="health-stat-info">
            <div className="health-stat-label">완료율</div>
            <div className="health-stat-value">
              {completionRate} <span className="health-stat-unit">%</span>
            </div>
          </div>
        </div>
      </div>

      {data.lastPlayedAt && (
        <div style={{
          fontSize: '12px',
          color: '#8b95a1',
          textAlign: 'center',
          marginTop: '12px'
        }}>
          마지막 플레이: {new Date(data.lastPlayedAt).toLocaleString('ko-KR')}
        </div>
      )}
    </>
  );
};

export default HealthMonitoring;
