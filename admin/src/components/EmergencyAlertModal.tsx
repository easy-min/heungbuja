import { Modal, Button } from './index';
import { type EmergencyReport } from '../types/emergency';

interface EmergencyAlertModalProps {
  isOpen: boolean;
  onClose: () => void;
  report: EmergencyReport | null;
  onAcknowledge?: (reportId: number) => void;
}

const EmergencyAlertModal = ({ 
  isOpen, 
  onClose, 
  report,
  onAcknowledge 
}: EmergencyAlertModalProps) => {
  if (!report) return null;

  const handleAcknowledge = () => {
    if (onAcknowledge) {
      onAcknowledge(report.id);
    }
    onClose();
  };

  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose}>
      <div className="modal-icon">🚨</div>
      <div className="modal-title emergency">긴급 신고 발생!</div>
      
      <div className="modal-detail" style={{ textAlign: 'left' }}>
        <div style={{ marginBottom: '10px' }}>
          <strong>신고 번호:</strong> #{report.id}
        </div>
        <div style={{ marginBottom: '10px' }}>
          <strong>어르신:</strong> {report.userName}
        </div>
        {report.userRoom && (
          <div style={{ marginBottom: '10px' }}>
            <strong>위치:</strong> {report.userRoom}
          </div>
        )}
        <div style={{ marginBottom: '10px' }}>
          <strong>신고 시간:</strong> {formatTime(report.reportedAt)}
        </div>
        {report.location && (
          <div style={{ marginBottom: '10px' }}>
            <strong>장소:</strong> {report.location}
          </div>
        )}
        {report.description && (
          <div style={{ marginBottom: '10px' }}>
            <strong>상세 내용:</strong> {report.description}
          </div>
        )}
      </div>

      <div className="modal-actions">
        <Button variant="success" onClick={handleAcknowledge}>
          확인했습니다
        </Button>
        <Button variant="primary" onClick={onClose}>
          닫기
        </Button>
      </div>
    </Modal>
  );
};

export default EmergencyAlertModal;