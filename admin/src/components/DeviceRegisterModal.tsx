import { useState, type FormEvent } from 'react';
import { Modal, Input, Button } from './index';
import { registerDevice } from '../api/device';
import { useDeviceStore } from '../stores';

interface DeviceRegisterModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const DeviceRegisterModal = ({ isOpen, onClose }: DeviceRegisterModalProps) => {
  const [serialNumber, setSerialNumber] = useState('');
  const [location, setLocation] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const addDevice = useDeviceStore((state) => state.addDevice);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccessMessage('');

    if (!serialNumber.trim()) {
      setError('기기 일련번호는 필수 입력 항목입니다.');
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await registerDevice({
        serialNumber: serialNumber.trim(),
        location: location.trim() || undefined,
      });

      // 스토어에 추가
      addDevice({
        id: response.id,
        serialNumber: response.serialNumber,
        location: response.location,
        isConnected: false,
        createdAt: response.createdAt,
      });

      setSuccessMessage(`기기가 성공적으로 등록되었습니다! (ID: ${response.id})`);

      // 2초 후 모달 닫기
      setTimeout(() => {
        handleClose();
      }, 2000);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '기기 등록에 실패했습니다.';
      setError(errorMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleClose = () => {
    setSerialNumber('');
    setLocation('');
    setError('');
    setSuccessMessage('');
    onClose();
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} maxWidth="500px">
      <div className="modal-icon">📱</div>
      <div className="modal-title primary">기기 등록</div>

      <form onSubmit={handleSubmit} style={{ textAlign: 'left', marginTop: '20px' }}>
        <Input
          label="기기 일련번호 (필수)"
          placeholder="예: DEVICE-2024-001"
          value={serialNumber}
          onChange={(e) => setSerialNumber(e.target.value)}
          disabled={isSubmitting}
          error={error && !serialNumber.trim() ? error : ''}
        />

        <Input
          label="설치 위치 (선택)"
          placeholder="예: 101호"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          disabled={isSubmitting}
        />

        {error && !(!serialNumber.trim()) && (
          <div className="error-message">{error}</div>
        )}

        {successMessage && (
          <div className="success-message">{successMessage}</div>
        )}

        <div className="modal-actions">
          <Button
            type="submit"
            variant="success"
            disabled={isSubmitting}
          >
            {isSubmitting ? '등록 중...' : '등록'}
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            취소
          </Button>
        </div>
      </form>
    </Modal>
  );
};

export default DeviceRegisterModal;