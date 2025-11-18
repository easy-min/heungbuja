import { useState, useEffect } from 'react';
import type { PeriodType, UserDetailData } from '../../types/device';
import {
  getUserHealthStats,
  getUserActionPerformance,
  getUserActivityTrend,
  getUserRecentActivities,
} from '../../api/device';
import PeriodTabs from './PeriodTabs';
import HealthMonitoring from './HealthMonitoring';
import ActionPerformance from './ActionPerformance';
import ActivityTrend from './ActivityTrend';
import RecentActivities from './RecentActivities';

interface UserDetailsPanelProps {
  userId: number;
  isOpen: boolean;
}

const UserDetailsPanel = ({ userId, isOpen }: UserDetailsPanelProps) => {
  const [selectedPeriod, setSelectedPeriod] = useState<PeriodType>(1);
  const [data, setData] = useState<UserDetailData>({
    healthStats: null,
    actionPerformance: [],
    activityTrend: [],
    recentActivities: [],
  });
  const [isLoading, setIsLoading] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);

  // 패널이 열릴 때 데이터 로드
  useEffect(() => {
    if (isOpen && !hasLoaded) {
      loadUserData();
      setHasLoaded(true);
    }
  }, [isOpen]);

  // 기간 변경 시 데이터 재로드
  useEffect(() => {
    if (hasLoaded) {
      loadUserData();
    }
  }, [selectedPeriod]);

  const loadUserData = async () => {
    setIsLoading(true);
    try {
      const [healthStats, actionPerformance, activityTrend, recentActivities] = await Promise.all([
        getUserHealthStats(userId, selectedPeriod),
        getUserActionPerformance(userId, selectedPeriod),
        getUserActivityTrend(userId, selectedPeriod),
        getUserRecentActivities(userId, 10),
      ]);

      setData({
        healthStats,
        actionPerformance,
        activityTrend,
        recentActivities,
      });
    } catch (error) {
      console.error('사용자 상세 데이터 로드 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) {
    return null;
  }

  return (
    <div className="du-details-panel open">
      {/* 건강 모니터링 */}
      <div className="du-detail-section">
        <h5>💪 건강 모니터링</h5>
        <PeriodTabs selectedPeriod={selectedPeriod} onPeriodChange={setSelectedPeriod} />
        <HealthMonitoring data={data.healthStats} isLoading={isLoading} />
      </div>

      {/* 동작별 수행도 */}
      <div className="du-detail-section">
        <h5>🎯 동작별 수행도</h5>
        <ActionPerformance data={data.actionPerformance} isLoading={isLoading} />
      </div>

      {/* 활동 추이 */}
      <div className="du-detail-section">
        <h5>📈 활동 추이</h5>
        <ActivityTrend data={data.activityTrend} isLoading={isLoading} />
      </div>

      {/* 최근 활동 */}
      <div className="du-detail-section">
        <h5>📊 최근 활동</h5>
        <RecentActivities data={data.recentActivities} isLoading={isLoading} />
      </div>
    </div>
  );
};

export default UserDetailsPanel;
