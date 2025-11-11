import { useState } from 'react';
import type { EmergencyReport } from '../types/emergency';
import EmergencyCard from './EmergencyCard';
import Loading from './Loading';
import '../styles/emergency-list.css';

interface EmergencyListProps {
  reports: EmergencyReport[];
  onResolve: (reportId: number) => Promise<void>;
  isLoading?: boolean;
}

const EmergencyList = ({ reports, onResolve, isLoading }: EmergencyListProps) => {
  const [resolvingId, setResolvingId] = useState<number | null>(null);

  const handleResolve = async (reportId: number) => {
    setResolvingId(reportId);
    try {
      await onResolve(reportId);
    } finally {
      setResolvingId(null);
    }
  };

  if (isLoading) {
    return <Loading text="신고 목록 불러오는 중..." />;
  }

  if (reports.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-icon">📋</div>
        <p>현재 신고 내역이 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="emergency-list">
      {reports.map((report) => (
        <EmergencyCard
          key={report.reportId}
          report={report}
          onResolve={handleResolve}
          isResolving={resolvingId === report.reportId}
        />
      ))}
    </div>
  );
};

export default EmergencyList;