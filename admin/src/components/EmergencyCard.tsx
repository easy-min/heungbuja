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
        <span className="report-id">신고 #{report.reportId}</span>
        <Badge status={report.status} />
      </div>

      <div className="card-body">
        <div className="card-row">
          <strong>어르신:</strong> {report.userName}
        </div>

        <div className="card-row">
          <strong>신고시간:</strong> {formatDate(report.reportedAt)}
        </div>

        {report.triggerWord && (
          <div className="card-row">
            <strong>트리거 단어:</strong> {report.triggerWord}
          </div>
        )}

        {report.message && (
          <div className="card-row">
            <strong>메시지:</strong> {report.message}
          </div>
        )}

        {report.isConfirmed !== undefined && (
          <div className="card-row">
            <strong>확인됨:</strong> {report.isConfirmed ? '예' : '아니오'}
          </div>
        )}
      </div>

      {!isResolved && !isFalseAlarm && (
        <Button
          variant="success"
          fullWidth
          onClick={() => onResolve(report.reportId)}
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