import  type { User, RegisterUserRequest, RegisterUserResponse } from '../types/user';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

/**
 * 어르신 목록 조회
 */
export const getUsers = async (): Promise<User[]> => {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`${API_BASE}/admins/users`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '어르신 목록을 불러오는데 실패했습니다.');
  }

  return response.json();
};

/**
 * 어르신 등록
 */
export const registerUser = async (userData: RegisterUserRequest): Promise<RegisterUserResponse> => {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`${API_BASE}/admins/users`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(userData),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '어르신 등록에 실패했습니다.');
  }

  return response.json();
};

/**
 * 특정 어르신 상세 조회
 */
export const getUserById = async (userId: number): Promise<User> => {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`${API_BASE}/admins/users/${userId}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || '어르신 정보를 불러오는데 실패했습니다.');
  }

  return response.json();
};