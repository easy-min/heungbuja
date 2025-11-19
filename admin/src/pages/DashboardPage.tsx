import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  EmergencyList,
  ActivityFeed,
  WebSocketStatus,
  DeviceRegisterModal,
  UserRegisterModal,
  EmergencyAlertModal,
  SimpleSongUploadModal,
  DeviceUserGrid,
} from '../components';
import DashboardHeader from '../components/DashboardHeader';
import SectionTitle from '../components/SectionTitle';
import { useWebSocket } from '../hooks/useWebSocket';
import {
  useEmergencyStore,
  useUserStore,
  useActivityStore,
  useNotificationStore,
  useDeviceStore,
} from '../stores';
import { getEmergencyReports, resolveEmergency } from '../api/emergency';
import { getUsers } from '../api/user';
import { type EmergencyReport } from '../types/emergency';
import { mockEmergencyReports, mockUsers, mockDevices } from '../mocks/mockData';
import '../styles/dashboard.css';
import '../styles/device-user.css';

const useMockData = import.meta.env.VITE_USE_MOCK === 'true';

const DashboardPage = () => {
  const navigate = useNavigate();

  // 모달 상태
  const [isDeviceModalOpen, setIsDeviceModalOpen] = useState(false);
  const [isUserModalOpen, setIsUserModalOpen] = useState(false);
  const [isEmergencyAlertOpen, setIsEmergencyAlertOpen] = useState(false);
  const [currentEmergencyAlert, setCurrentEmergencyAlert] = useState<EmergencyReport | null>(null);
  const [isSongUploadModalOpen, setIsSongUploadModalOpen] = useState(false);

  // 응급 신고 더보기 상태
  const [showAllEmergencies, setShowAllEmergencies] = useState(false);

  // 스토어
  const reports = useEmergencyStore((state) => state.reports);
  const setReports = useEmergencyStore((state) => state.setReports);
  const updateReport = useEmergencyStore((state) => state.updateReport);
  const isLoadingReports = useEmergencyStore((state) => state.isLoading);
  const setLoadingReports = useEmergencyStore((state) => state.setLoading);

  const setUsers = useUserStore((state) => state.setUsers);
  const setLoadingUsers = useUserStore((state) => state.setLoading);

  const setDevices = useDeviceStore((state) => state.setDevices);

  const activities = useActivityStore((state) => state.activities);
  
  const clearUnread = useNotificationStore((state) => state.clearUnread);

  // WebSocket 연결
  const { isConnected, isConnecting, connect } = useWebSocket({
    onConnect: () => {
      console.log('✅ Dashboard: WebSocket connected');
    },
    onDisconnect: () => {
      console.log('❌ Dashboard: WebSocket disconnected');
    },
  });

  // 초기 데이터 로드
  useEffect(() => {
    // 토큰 확인
    const token = localStorage.getItem('accessToken');
    if (!token) {
      navigate('/login');
      return;
    }

    loadDashboardData();
    
    // Mock 모드가 아닐 때만 WebSocket 연결
    if (!useMockData) {
      connect();
    }
  }, []);

  // 대시보드 데이터 로드
  const loadDashboardData = async () => {
    await Promise.all([
      loadEmergencyReports(),
      loadUsers(),
    ]);
  };

  // 신고 목록 로드
  const loadEmergencyReports = async () => {
    setLoadingReports(true);
    try {
      if (useMockData) {
        // Mock 데이터 사용
        await new Promise(resolve => setTimeout(resolve, 500)); // 로딩 시뮬레이션
        setReports(mockEmergencyReports);
      } else {
        const data = await getEmergencyReports();
        setReports(data);
      }
    } catch (error) {
      console.error('신고 목록 로드 실패:', error);
    } finally {
      setLoadingReports(false);
    }
  };

  // 어르신 목록 로드
  const loadUsers = async () => {
    setLoadingUsers(true);
    try {
      if (useMockData) {
        // Mock 데이터 사용
        await new Promise(resolve => setTimeout(resolve, 500)); // 로딩 시뮬레이션
        setUsers(mockUsers);
        setDevices(mockDevices);
      } else {
        const data = await getUsers();
        setUsers(data);
      }
    } catch (error) {
      console.error('어르신 목록 로드 실패:', error);
    } finally {
      setLoadingUsers(false);
    }
  };

  // 신고 처리
  const handleResolveEmergency = async (reportId: number) => {
    try {
      if (useMockData) {
        // Mock 모드: 상태만 업데이트
        await new Promise(resolve => setTimeout(resolve, 500));
        updateReport(reportId, {
          status: 'RESOLVED',
        });
      } else {
        const updatedReport = await resolveEmergency(reportId);
        // 백엔드에서 받은 업데이트된 신고 정보로 상태 갱신
        updateReport(reportId, updatedReport);
      }
    } catch (error) {
      console.error('신고 처리 실패:', error);
      alert('신고 처리에 실패했습니다.');
    }
  };

  // 알림 아이콘 클릭
  const handleNotificationClick = () => {
    clearUnread();
  };

  // 긴급 신고 알림 (WebSocket을 통해 새 신고가 들어오면 자동으로 처리됨)
  useEffect(() => {
    // 가장 최근 PENDING/CONFIRMED 신고가 있으면 알림 표시
    const latestEmergency = reports.find(
      (r) => r.status === 'CONFIRMED'
    );
    
    if (latestEmergency && !currentEmergencyAlert) {
      setCurrentEmergencyAlert(latestEmergency);
      setIsEmergencyAlertOpen(true);
    }
  }, [reports]);

  return (
    <div className="dashboard-container">
      <div className="dashboard-content">
        {/* 헤더 */}
        <DashboardHeader onNotificationClick={handleNotificationClick} />

        {/* 등록 버튼 */}
        <div className="section">
          <Button
            variant="primary"
            onClick={() => setIsDeviceModalOpen(true)}
            style={{ marginRight: '10px' }}
          >
            📱 기기 등록
          </Button>
          <Button
            variant="success"
            onClick={() => setIsUserModalOpen(true)}
            style={{ marginRight: '10px' }}
          >
            👴 어르신 등록
          </Button>
          <Button
            variant="primary"
            onClick={() => navigate('/visualization')}
            style={{ marginRight: '10px' }}
          >
            🎵 곡 시각화
          </Button>
          <Button
            variant="success"
            onClick={() => setIsSongUploadModalOpen(true)}
          >
            🎵 곡 간편 등록
          </Button>
        </div>

        {/* 신고 리스트 */}
        <div className="section">
          <SectionTitle>📊 실시간 신고 리스트</SectionTitle>
          <EmergencyList
            reports={showAllEmergencies ? reports : reports.slice(0, 4)}
            onResolve={handleResolveEmergency}
            isLoading={isLoadingReports}
          />
          {reports.length > 4 && (
            <div style={{ textAlign: 'center', marginTop: '20px' }}>
              <Button
                variant="secondary"
                onClick={() => setShowAllEmergencies(!showAllEmergencies)}
              >
                {showAllEmergencies ? '접기' : `더 보기 (${reports.length - 4}개)`}
              </Button>
            </div>
          )}
        </div>

        {/* 어르신 현황 */}
        {/* <div className="section">
          <SectionTitle>🧑‍🦳 담당 어르신 현황</SectionTitle>
          <UserGrid users={users} isLoading={isLoadingUsers} />
        </div> */}

        {/* 기기-사용자 관리 */}
        <div className="section">
          <SectionTitle>📱 기기 및 사용자 관리</SectionTitle>
          <DeviceUserGrid />
        </div>

        {/* 활동 피드 */}
        <div className="section">
          <SectionTitle>📝 활동 피드</SectionTitle>
          <ActivityFeed activities={activities} />
        </div>

        {/* WebSocket 상태 */}
        <WebSocketStatus 
          isConnected={isConnected} 
          isConnecting={isConnecting} 
        />
      </div>

      {/* 모달들 */}
      <DeviceRegisterModal
        isOpen={isDeviceModalOpen}
        onClose={() => setIsDeviceModalOpen(false)}
      />

      <UserRegisterModal
        isOpen={isUserModalOpen}
        onClose={() => {
          setIsUserModalOpen(false);
          loadUsers(); // 어르신 등록 후 목록 새로고침
        }}
      />

      <EmergencyAlertModal
        isOpen={isEmergencyAlertOpen}
        onClose={() => {
          setIsEmergencyAlertOpen(false);
          setCurrentEmergencyAlert(null);
        }}
        report={currentEmergencyAlert}
        onAcknowledge={(reportId) => {
          console.log('Emergency acknowledged:', reportId);
        }}
      />

      <SimpleSongUploadModal
        isOpen={isSongUploadModalOpen}
        onClose={() => setIsSongUploadModalOpen(false)}
      />
    </div>
  );
};

export default DashboardPage;