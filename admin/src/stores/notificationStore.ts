import { create } from 'zustand';

interface NotificationStore {
  // 상태
  unreadCount: number;
  showBadge: boolean;
  
  // 액션
  incrementUnread: () => void;
  clearUnread: () => void;
  setUnreadCount: (count: number) => void;
  toggleBadge: (show: boolean) => void;
}

export const useNotificationStore = create<NotificationStore>((set) => ({
  // 초기 상태
  unreadCount: 0,
  showBadge: false,
  
  // 액션 구현
  incrementUnread: () => set((state) => ({
    unreadCount: state.unreadCount + 1,
    showBadge: true,
  })),
  
  clearUnread: () => set({
    unreadCount: 0,
    showBadge: false,
  }),
  
  setUnreadCount: (count) => set({
    unreadCount: count,
    showBadge: count > 0,
  }),
  
  toggleBadge: (show) => set({ showBadge: show }),
}));