import type { ActivityTrendPoint } from '../../types/device';

interface ActivityTrendProps {
  data: ActivityTrendPoint[];
  isLoading: boolean;
}

const ActivityTrend = ({ data, isLoading }: ActivityTrendProps) => {
  if (isLoading) {
    return (
      <div className="du-loading">
        <p>활동 추이를 불러오는 중...</p>
      </div>
    );
  }

  if (!data || !Array.isArray(data) || data.length === 0) {
    return (
      <div className="du-empty">
        <p>활동 추이 데이터가 없습니다</p>
      </div>
    );
  }

  // 최대값 계산 (차트 스케일용)
  const maxExerciseTime = Math.max(...data.map((d) => d.exerciseTime), 1);

  return (
    <div className="activity-trend-container">
      <div className="activity-trend-chart">
        {data.map((point, index) => {
          const exerciseHeight = (point.exerciseTime / maxExerciseTime) * 100;
          const accuracyHeight = point.accuracy;

          return (
            <div key={index} className="trend-chart-item">
              <div className="trend-bars">
                <div className="trend-bar-wrapper">
                  <div
                    className="trend-bar trend-bar-exercise"
                    style={{ height: `${exerciseHeight}%` }}
                    title={`운동시간: ${point.exerciseTime}분`}
                  />
                </div>
                <div className="trend-bar-wrapper">
                  <div
                    className="trend-bar trend-bar-accuracy"
                    style={{ height: `${accuracyHeight}%` }}
                    title={`정확도: ${point.accuracy}%`}
                  />
                </div>
              </div>
              <div className="trend-chart-label">
                {new Date(point.date).toLocaleDateString('ko-KR', {
                  month: 'short',
                  day: 'numeric',
                })}
              </div>
            </div>
          );
        })}
      </div>

      <div className="activity-trend-legend">
        <div className="legend-item">
          <div className="legend-color legend-color-exercise" />
          <span>운동 시간 (분)</span>
        </div>
        <div className="legend-item">
          <div className="legend-color legend-color-accuracy" />
          <span>정확도 (%)</span>
        </div>
      </div>
    </div>
  );
};

export default ActivityTrend;
