import { useEffect, useState } from 'react';
import type { Device } from '../../types/device';
import type { User } from '../../types/user';
import type { EmergencyReport } from '../../types/emergency';
import { getDevices } from '../../api/device';
import { getUsers } from '../../api/user';
import { getEmergencyReports } from '../../api/emergency';
import DeviceUserCard from './DeviceUserCard';
import UserDetailsModal from './UserDetailsModal';

const DeviceUserGrid = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [emergencies, setEmergencies] = useState<EmergencyReport[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  // const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);
  const [isUserDetailsOpen, setIsUserDetailsOpen] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setIsLoading(true);
    setError(null);

    try {
      const [devicesData, usersData, emergenciesData] = await Promise.all([
        getDevices(),
        getUsers(),
        getEmergencyReports(),
      ]);

      setDevices(devicesData);
      setUsers(usersData);
      setEmergencies(emergenciesData);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '데이터를 불러올 수 없습니다.';
      setError(errorMessage);
      console.error('기기-사용자 데이터 로드 실패:', err);
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="device-user-grid">
        <div className="du-loading-full">
          <p>기기 및 사용자 데이터를 불러오는 중...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="device-user-grid">
        <div className="du-error">
          <div className="icon">❌</div>
          <p>{error}</p>
        </div>
      </div>
    );
  }

  if (devices.length === 0) {
    return (
      <div className="device-user-grid">
        <div className="du-empty-full">
          <div className="icon">📱</div>
          <p>등록된 기기가 없습니다</p>
        </div>
      </div>
    );
  }

  return (
    <div className="device-user-grid">
      {devices.map((device) => {
        const user = users.find((u) => u.deviceId === device.id);
        const hasEmergency = user
          ? emergencies.some((e) => e.userId === user.id && e.status === 'CONFIRMED')
          : false;

        return (
          <DeviceUserCard
            key={device.id}
            device={device}
            user={user}
            hasEmergency={hasEmergency}
            onClickUser={(d, u) => {
              if (!u) return;
              // setSelectedDevice(d);
              setSelectedUser(u);
              setIsUserDetailsOpen(true);
            }}
          />
        );
      })}

      <UserDetailsModal
        isOpen={isUserDetailsOpen}
        onClose={() => setIsUserDetailsOpen(false)}
        user={selectedUser}
      />
    </div>
  );
};

export default DeviceUserGrid;
