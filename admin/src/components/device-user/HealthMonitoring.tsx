import type { HealthStats } from '../../types/device';

interface HealthMonitoringProps {
  data: HealthStats | null;
  isLoading: boolean;
}

const HealthMonitoring = ({ data, isLoading }: HealthMonitoringProps) => {
  if (isLoading) {
    return (
      <div className="du-loading">
        <p>건강 데이터를 불러오는 중...</p>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="du-empty">
        <p>건강 데이터가 없습니다</p>
      </div>
    );
  }

  const getHeartRateColor = (status: string) => {
    switch (status) {
      case 'normal':
        return '#10b981';
      case 'high':
        return '#ef4444';
      case 'low':
        return '#f59e0b';
      default:
        return '#6b7280';
    }
  };

  const getHeartRateLabel = (status: string) => {
    switch (status) {
      case 'normal':
        return '정상';
      case 'high':
        return '높음';
      case 'low':
        return '낮음';
      default:
        return '측정 불가';
    }
  };

  return (
    <div className="health-stats-grid">
      <div className="health-stat-card">
        <div className="health-stat-icon">💓</div>
        <div className="health-stat-info">
          <div className="health-stat-label">심박수</div>
          <div className="health-stat-value">
            {data.heartRate} <span className="health-stat-unit">bpm</span>
          </div>
          <div
            className="health-stat-status"
            style={{ color: getHeartRateColor(data.heartRateStatus) }}
          >
            {getHeartRateLabel(data.heartRateStatus)}
          </div>
        </div>
      </div>

      <div className="health-stat-card">
        <div className="health-stat-icon">👟</div>
        <div className="health-stat-info">
          <div className="health-stat-label">걸음수</div>
          <div className="health-stat-value">
            {data.steps.toLocaleString()} <span className="health-stat-unit">보</span>
          </div>
        </div>
      </div>

      <div className="health-stat-card">
        <div className="health-stat-icon">🔥</div>
        <div className="health-stat-info">
          <div className="health-stat-label">칼로리</div>
          <div className="health-stat-value">
            {data.calories.toLocaleString()} <span className="health-stat-unit">kcal</span>
          </div>
        </div>
      </div>

      <div className="health-stat-card">
        <div className="health-stat-icon">⏱️</div>
        <div className="health-stat-info">
          <div className="health-stat-label">운동 시간</div>
          <div className="health-stat-value">
            {data.exerciseTime} <span className="health-stat-unit">분</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default HealthMonitoring;
