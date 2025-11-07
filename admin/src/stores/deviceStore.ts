import { create } from 'zustand';
import type { Device } from '../types/device';

interface DeviceStore {
  // 상태
  devices: Device[];
  isLoading: boolean;
  error: string | null;
  
  // 액션
  setDevices: (devices: Device[]) => void;
  addDevice: (device: Device) => void;
  updateDevice: (deviceId: number, updates: Partial<Device>) => void;
  removeDevice: (deviceId: number) => void;
  setLoading: (isLoading: boolean) => void;
  setError: (error: string | null) => void;
  clearError: () => void;
  
  // 헬퍼 함수
  getAvailableDevices: () => Device[];
  getDeviceById: (deviceId: number) => Device | undefined;
}

export const useDeviceStore = create<DeviceStore>((set, get) => ({
  // 초기 상태
  devices: [],
  isLoading: false,
  error: null,
  
  // 액션 구현
  setDevices: (devices) => set({ devices }),
  
  addDevice: (device) => set((state) => ({
    devices: [...state.devices, device],
  })),
  
  updateDevice: (deviceId, updates) => set((state) => ({
    devices: state.devices.map((device) =>
      device.id === deviceId ? { ...device, ...updates } : device
    ),
  })),
  
  removeDevice: (deviceId) => set((state) => ({
    devices: state.devices.filter((device) => device.id !== deviceId),
  })),
  
  setLoading: (isLoading) => set({ isLoading }),
  
  setError: (error) => set({ error }),
  
  clearError: () => set({ error: null }),
  
  // 헬퍼 함수
  getAvailableDevices: () => {
    return get().devices.filter((device) => !device.connectedUserId);
  },
  
  getDeviceById: (deviceId) => {
    return get().devices.find((device) => device.id === deviceId);
  },
}));