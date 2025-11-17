import { create } from 'zustand';

interface EnvironmentState {
  isRaspberryPi: boolean;
  deviceId: string | undefined;
  setEnvironment: (isRaspberryPi: boolean, deviceId?: string) => void;
}

export const useEnvironmentStore = create<EnvironmentState>((set) => ({
  isRaspberryPi: false,
  deviceId: undefined,
  setEnvironment: (isRaspberryPi: boolean, deviceId?: string) => 
    set({ isRaspberryPi, deviceId }),
}));