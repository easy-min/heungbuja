import type { EmergencyReport } from '../types/emergency';
import Badge from './Badge';
import Button from './Button';
import '../styles/emergency-card.css';

interface EmergencyCardProps {
  report: EmergencyReport;
  onResolve: (reportId: number) => void;
  isResolving?: boolean;
}

const EmergencyCard = ({ report, onResolve, isResolving }: EmergencyCardProps) => {
  const isResolved = report.status === 'RESOLVED';
  const isFalseAlarm = report.status === 'FALSE_ALARM';

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className={`emergency-card ${report.status.toLowerCase()}`}>
      <div className="card-header">
        <span className="report-id">신고 #{report.id}</span>
        <Badge status={report.status} />
      </div>

      <div className="card-body">
        <div className="card-row">
          <strong>어르신:</strong> {report.userName}
        </div>
        
        {report.userRoom && (
          <div className="card-row">
            <strong>위치:</strong> {report.userRoom}
          </div>
        )}
        
        <div className="card-row">
          <strong>신고시간:</strong> {formatDate(report.reportedAt)}
        </div>

        {report.location && (
          <div className="card-row">
            <strong>장소:</strong> {report.location}
          </div>
        )}

        {report.description && (
          <div className="card-row">
            <strong>상세:</strong> {report.description}
          </div>
        )}

        {isResolved && report.resolvedAt && (
          <div className="card-row resolved-info">
            <strong>처리완료:</strong> {formatDate(report.resolvedAt)}
          </div>
        )}
      </div>

      {!isResolved && !isFalseAlarm && (
        <Button
          variant="success"
          fullWidth
          onClick={() => onResolve(report.id)}
          disabled={isResolving}
        >
          {isResolving ? '처리 중...' : '처리 완료'}
        </Button>
      )}

      {isResolved && (
        <div className="resolved-label">✓ 처리 완료</div>
      )}
    </div>
  );
};

export default EmergencyCard;