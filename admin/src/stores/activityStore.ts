import { create } from 'zustand';
import type { ActivityItem } from '../types/activity';

interface ActivityStore {
  // 상태
  activities: ActivityItem[];
  maxActivities: number; // 최대 활동 수 (오래된 것 자동 삭제)
  
  // 액션
  addActivity: (activity: ActivityItem) => void;
  clearActivities: () => void;
  removeActivity: (activityId: string) => void;
  setMaxActivities: (max: number) => void;
}

export const useActivityStore = create<ActivityStore>((set) => ({
  // 초기 상태
  activities: [],
  maxActivities: 50, // 최근 50개만 유지
  
  // 액션 구현
  addActivity: (activity) => set((state) => {
    const newActivities = [activity, ...state.activities];
    
    // maxActivities를 초과하면 오래된 것 제거
    if (newActivities.length > state.maxActivities) {
      return { activities: newActivities.slice(0, state.maxActivities) };
    }
    
    return { activities: newActivities };
  }),
  
  clearActivities: () => set({ activities: [] }),
  
  removeActivity: (activityId) => set((state) => ({
    activities: state.activities.filter((activity) => activity.id !== activityId),
  })),
  
  setMaxActivities: (max) => set({ maxActivities: max }),
}));