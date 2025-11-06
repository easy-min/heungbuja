import type { User } from '../types/user';
import UserCard from './UserCard';
import Loading from './Loading';
import '../styles/user-grid.css';

interface UserGridProps {
  users: User[];
  isLoading?: boolean;
}

const UserGrid = ({ users, isLoading }: UserGridProps) => {
  if (isLoading) {
    return <Loading text="어르신 목록 불러오는 중..." />;
  }

  if (users.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-icon">👴</div>
        <p>등록된 어르신이 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="users-grid">
      {users.map((user) => (
        <UserCard key={user.id} user={user} />
      ))}
    </div>
  );
};

export default UserGrid;