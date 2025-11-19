import type { User } from '../../types/user';
import UserDetailsPanel from './UserDetailsPanel';
import styles from '../../styles/UserDetailsModal.module.css';

interface UserDetailsModalProps {
  isOpen: boolean;
  onClose: () => void;
  user: User | null;
}

const UserDetailsModal = ({ isOpen, onClose, user }: UserDetailsModalProps) => {
  if (!isOpen || !user) return null;

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div
        className={styles.modalBox}
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.modalHeader}>
          <button className={styles.closeButton} onClick={onClose}>
            ×
          </button>
          <div className={styles.headerContent}>
            <div className={styles.iconWrapper}>
              <span className={styles.userIcon}>💪</span>
            </div>
            <h2 className={styles.modalTitle}>{user.name} 상세 정보</h2>
            <p className={styles.modalSubtitle}>
              게임 통계와 동작별 수행도를 한눈에 확인할 수 있습니다.
            </p>
          </div>
        </div>

        <div className={styles.modalBody}>
          <UserDetailsPanel
            userId={user.id}
            isOpen={true}
            onFirstOpen={() => {}}
            hasLoadedData={false}
          />
        </div>

        {/* 필요 없으면 modalFooter 블록은 통째로 빼도 됩니다 */}
        {/* <div className={styles.modalFooter}>
          <button
            className={`${styles.footerButton} ${styles.footerButtonGhost}`}
            onClick={onClose}
          >
            닫기
          </button>
        </div> */}
      </div>
    </div>
  );
};

export default UserDetailsModal;
