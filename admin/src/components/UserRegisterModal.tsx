import { useState, type FormEvent, useEffect } from 'react';
import { Modal, Input, Select, Textarea, Button } from './index';
import { registerUser } from '../api/user';
import { getDevices } from '../api/device';
import { useUserStore, useDeviceStore } from '../stores';
import { type Gender } from '../types/user';

interface UserRegisterModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const UserRegisterModal = ({ isOpen, onClose }: UserRegisterModalProps) => {
  const [name, setName] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [gender, setGender] = useState<Gender | ''>('');
  const [emergencyContact, setEmergencyContact] = useState('');
  const [deviceId, setDeviceId] = useState('');
  const [medicalNotes, setMedicalNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const addUser = useUserStore((state) => state.addUser);
  const devices = useDeviceStore((state) => state.devices);
  const setDevices = useDeviceStore((state) => state.setDevices);

  // availableDevices를 컴포넌트 내부에서 계산
  const availableDevices = devices.filter((device) => !device.connectedUserId);

  // 모달이 열릴 때 기기 목록 로드
  useEffect(() => {
    if (isOpen) {
      loadAvailableDevices();
    }
  }, [isOpen]);

  const loadAvailableDevices = async () => {
    try {
      const deviceList = await getDevices(true); // availableOnly=true
      setDevices(deviceList);
    } catch (err) {
      console.error('기기 목록 로드 실패:', err);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccessMessage('');

    if (!name.trim()) {
      setError('이름은 필수 입력 항목입니다.');
      return;
    }

    if (!deviceId) {
      setError('연결할 기기를 선택해주세요.');
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await registerUser({
        name: name.trim(),
        birthDate: birthDate || undefined,
        gender: gender || undefined,
        emergencyContact: emergencyContact.trim() || undefined,
        medicalNotes: medicalNotes.trim() || undefined,
        deviceId: parseInt(deviceId),
      });

      // 스토어에 추가 (실제로는 전체 사용자 정보가 필요하므로 기본값 설정)
      addUser({
        id: response.id,
        name: response.name,
        birthDate: birthDate || undefined,
        gender: gender || undefined,
        emergencyContact: emergencyContact.trim() || undefined,
        medicalNotes: medicalNotes.trim() || undefined,
        deviceId: response.deviceId,
        status: 'ACTIVE',
        createdAt: response.createdAt,
      });

      setSuccessMessage(`어르신이 성공적으로 등록되었습니다! (ID: ${response.id})`);

      // 2초 후 모달 닫기
      setTimeout(() => {
        handleClose();
      }, 2000);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '어르신 등록에 실패했습니다.';
      setError(errorMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleClose = () => {
    setName('');
    setBirthDate('');
    setGender('');
    setEmergencyContact('');
    setDeviceId('');
    setMedicalNotes('');
    setError('');
    setSuccessMessage('');
    onClose();
  };

  const genderOptions = [
    { value: '', label: '성별 선택 (선택)' },
    { value: 'MALE', label: '남성' },
    { value: 'FEMALE', label: '여성' },
    { value: 'OTHER', label: '기타' },
  ];

  const deviceOptions = [
    { value: '', label: '연결할 기기 선택 (필수)' },
    ...availableDevices.map((device) => ({
      value: device.id.toString(),
      label: `${device.serialNumber}${device.location ? ` (${device.location})` : ''}`,
    })),
  ];

  // 사용 가능한 기기가 없을 때
  if (isOpen && availableDevices.length === 0 && devices.length > 0) {
    // 로딩이 끝났는데도 사용 가능한 기기가 없는 경우
    return (
      <Modal isOpen={isOpen} onClose={handleClose} maxWidth="500px">
        <div className="modal-icon">⚠️</div>
        <div className="modal-title">사용 가능한 기기가 없습니다</div>
        <div className="modal-detail">
          먼저 기기를 등록해주세요.
        </div>
        <div className="modal-actions">
          <Button variant="primary" onClick={handleClose}>
            확인
          </Button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal isOpen={isOpen} onClose={handleClose} maxWidth="500px">
      <div className="modal-icon">👴</div>
      <div className="modal-title primary">어르신 등록</div>

      <form onSubmit={handleSubmit} style={{ textAlign: 'left', marginTop: '20px' }}>
        <Input
          label="이름 (필수)"
          placeholder="어르신 성함"
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={isSubmitting}
          error={error && !name.trim() ? '이름은 필수입니다' : ''}
        />

        <Input
          label="생년월일 (선택)"
          type="date"
          value={birthDate}
          onChange={(e) => setBirthDate(e.target.value)}
          disabled={isSubmitting}
        />

        <Select
          label="성별 (선택)"
          options={genderOptions}
          value={gender}
          onChange={(e) => setGender(e.target.value as Gender | '')}
          disabled={isSubmitting}
        />

        <Input
          label="비상 연락처 (선택)"
          placeholder="010-0000-0000"
          value={emergencyContact}
          onChange={(e) => setEmergencyContact(e.target.value)}
          disabled={isSubmitting}
        />

        <Select
          label="연결할 기기 (필수)"
          options={deviceOptions}
          value={deviceId}
          onChange={(e) => setDeviceId(e.target.value)}
          disabled={isSubmitting}
          error={error && !deviceId ? '기기 선택은 필수입니다' : ''}
        />

        <Textarea
          label="의료 특이사항 (선택)"
          placeholder="알레르기, 복용 약물 등"
          value={medicalNotes}
          onChange={(e) => setMedicalNotes(e.target.value)}
          disabled={isSubmitting}
          rows={3}
        />

        {error && name.trim() && deviceId && (
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

export default UserRegisterModal;