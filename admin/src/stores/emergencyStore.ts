import { create } from 'zustand';
import type { EmergencyReport } from '../types/emergency';

interface EmergencyStore {
  // 상태
  reports: EmergencyReport[];
  isLoading: boolean;
  error: string | null;
  
  // 액션
  setReports: (reports: EmergencyReport[]) => void;
  addReport: (report: EmergencyReport) => void;
  updateReport: (reportId: number, updates: Partial<EmergencyReport>) => void;
  removeReport: (reportId: number) => void;
  setLoading: (isLoading: boolean) => void;
  setError: (error: string | null) => void;
  clearError: () => void;
}

export const useEmergencyStore = create<EmergencyStore>((set) => ({
  // 초기 상태
  reports: [],
  isLoading: false,
  error: null,
  
  // 액션 구현
  setReports: (reports) => set({ reports }),
  
  addReport: (report) => set((state) => ({
    reports: [report, ...state.reports], // 최신 신고를 맨 앞에
  })),
  
  updateReport: (reportId, updates) => set((state) => ({
    reports: state.reports.map((report) =>
      report.reportId === reportId ? { ...report, ...updates } : report
    ),
  })),

  removeReport: (reportId) => set((state) => ({
    reports: state.reports.filter((report) => report.reportId !== reportId),
  })),
  
  setLoading: (isLoading) => set({ isLoading }),
  
  setError: (error) => set({ error }),
  
  clearError: () => set({ error: null }),
}));