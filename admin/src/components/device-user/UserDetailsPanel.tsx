import { useState, useEffect, useRef } from 'react';
import type { PeriodType, UserDetailData } from '../../types/device';
import {
  getUserGameStats,
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
  onFirstOpen: () => void;
  hasLoadedData: boolean;
}

const UserDetailsPanel = ({ userId, isOpen, onFirstOpen, hasLoadedData }: UserDetailsPanelProps) => {
  const [selectedPeriod, setSelectedPeriod] = useState<PeriodType>(1);
  const [data, setData] = useState<UserDetailData>({
    gameStats: null,
    actionPerformance: null,
    activityTrend: [],
    recentActivities: [],
  });
  const [isLoading, setIsLoading] = useState(false);
  const loadingRef = useRef(false); // 중복 호출 방지

  // 패널이 처음 열릴 때 데이터 로드 (useEffect 사용)
  useEffect(() => {
    const loadInitialData = async () => {
      if (!isOpen || hasLoadedData || loadingRef.current) return;

      loadingRef.current = true;
      setIsLoading(true);
      onFirstOpen(); // 부모에게 로드 시작 알림

      try {
        const results = await Promise.allSettled([
          getUserGameStats(userId),
          getUserActionPerformance(userId, selectedPeriod),
          getUserActivityTrend(userId, selectedPeriod),
          getUserRecentActivities(userId, 10),
        ]);

        setData({
          gameStats: results[0].status === 'fulfilled' ? results[0].value : null,
          actionPerformance: results[1].status === 'fulfilled' ? results[1].value : null,
          activityTrend: results[2].status === 'fulfilled' ? results[2].value : [],
          recentActivities: results[3].status === 'fulfilled' ? results[3].value : [],
        });

        results.forEach((result, index) => {
          if (result.status === 'rejected') {
            const apiNames = ['게임 통계', '동작별 수행도', '활동 추이', '최근 활동'];
            console.error(`${apiNames[index]} 로드 실패:`, result.reason);
          }
        });
      } catch (error) {
        console.error('사용자 상세 데이터 로드 실패:', error);
      } finally {
        setIsLoading(false);
        loadingRef.current = false;
      }
    };

    loadInitialData();
  }, [isOpen, hasLoadedData, userId, selectedPeriod, onFirstOpen]);

  // 기간 변경 시 수행도와 추이만 재로드
  const handlePeriodChange = async (period: PeriodType) => {
    setSelectedPeriod(period);
    setIsLoading(true);

    try {
      const results = await Promise.allSettled([
        getUserActionPerformance(userId, period),
        getUserActivityTrend(userId, period),
      ]);

      setData((prev) => ({
        ...prev,
        actionPerformance: results[0].status === 'fulfilled' ? results[0].value : null,
        activityTrend: results[1].status === 'fulfilled' ? results[1].value : [],
      }));

      results.forEach((result, index) => {
        if (result.status === 'rejected') {
          const apiNames = ['동작별 수행도', '활동 추이'];
          console.error(`${apiNames[index]} 로드 실패:`, result.reason);
        }
      });
    } catch (error) {
      console.error('기간별 데이터 로드 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) {
    return null;
  }

  return (
    <div className="du-details-panel open">
      {/* 게임 통계 */}
      <div className="du-detail-section">
        <h5>💪 게임 통계</h5>
        <HealthMonitoring data={data.gameStats} isLoading={isLoading} />
      </div>

      {/* 동작별 수행도 */}
      <div className="du-detail-section">
        <h5>🎯 동작별 수행도</h5>
        <PeriodTabs selectedPeriod={selectedPeriod} onPeriodChange={handlePeriodChange} />
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
