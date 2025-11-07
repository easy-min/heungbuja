import { STORAGE_KEYS } from './constants';

/**
 * 사용자 ID 가져오기
 */
export const getUserId = (): string => {
  const userId = localStorage.getItem(STORAGE_KEYS.USER_ID);
  
  if (!userId) {
    throw new Error('사용자 ID가 설정되지 않았습니다. 초기 설정을 진행해주세요.');
  }
  
  return userId;
};

/**
 * 사용자 ID 저장하기
 */
export const saveUserId = (userId: string): void => {
  if (!userId || userId.trim() === '') {
    throw new Error('유효하지 않은 사용자 ID입니다.');
  }
  
  localStorage.setItem(STORAGE_KEYS.USER_ID, userId.trim());
};

/**
 * 사용자 이름 가져오기
 */
export const getUserName = (): string | null => {
  return localStorage.getItem(STORAGE_KEYS.USER_NAME);
};

/**
 * 사용자 이름 저장하기
 */
export const saveUserName = (userName: string): void => {
  if (!userName || userName.trim() === '') {
    throw new Error('유효하지 않은 사용자 이름입니다.');
  }
  
  localStorage.setItem(STORAGE_KEYS.USER_NAME, userName.trim());
};

/**
 * 사용자 설정 초기화
 */
export const clearUserConfig = (): void => {
  localStorage.removeItem(STORAGE_KEYS.USER_ID);
  localStorage.removeItem(STORAGE_KEYS.USER_NAME);
};

/**
 * 사용자 설정 여부 확인
 */
export const isUserConfigured = (): boolean => {
  return localStorage.getItem(STORAGE_KEYS.USER_ID) !== null;
};

/**
 * 기기 정보 가져오기
 */
export const getDeviceInfo = () => {
  return {
    userId: getUserId(),
    userName: getUserName(),
    platform: navigator.platform,
    userAgent: navigator.userAgent,
  };
};