import { create } from 'zustand';
import type { User } from '../types/user';

interface UserStore {
  // 상태
  users: User[];
  isLoading: boolean;
  error: string | null;
  
  // 액션
  setUsers: (users: User[]) => void;
  addUser: (user: User) => void;
  updateUser: (userId: number, updates: Partial<User>) => void;
  removeUser: (userId: number) => void;
  setLoading: (isLoading: boolean) => void;
  setError: (error: string | null) => void;
  clearError: () => void;
  
  // 헬퍼 함수
  getUserById: (userId: number) => User | undefined;
  getEmergencyUsers: () => User[];
  getWarningUsers: () => User[];
}

export const useUserStore = create<UserStore>((set, get) => ({
  // 초기 상태
  users: [],
  isLoading: false,
  error: null,
  
  // 액션 구현
  setUsers: (users) => set({ users }),
  
  addUser: (user) => set((state) => ({
    users: [...state.users, user],
  })),
  
  updateUser: (userId, updates) => set((state) => ({
    users: state.users.map((user) =>
      user.id === userId ? { ...user, ...updates } : user
    ),
  })),
  
  removeUser: (userId) => set((state) => ({
    users: state.users.filter((user) => user.id !== userId),
  })),
  
  setLoading: (isLoading) => set({ isLoading }),
  
  setError: (error) => set({ error }),
  
  clearError: () => set({ error: null }),
  
  // 헬퍼 함수
  getUserById: (userId) => {
    return get().users.find((user) => user.id === userId);
  },
  
  getEmergencyUsers: () => {
    return get().users.filter((user) => user.status === 'EMERGENCY');
  },
  
  getWarningUsers: () => {
    return get().users.filter((user) => user.status === 'WARNING');
  },
}));