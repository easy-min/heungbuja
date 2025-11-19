import { getApiBaseUrl } from './utils';
import type { Song, VisualizationData } from '../types/visualization';

const API_BASE = getApiBaseUrl();

/**
 * 곡 목록 조회
 */
export const getSongs = async (): Promise<Song[]> => {
  const token = localStorage.getItem('accessToken');

  const response = await fetch(`${API_BASE}/admins/songs`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('곡 목록을 불러오는데 실패했습니다.');
  }

  return response.json();
};

/**
 * 시각화 데이터 조회
 */
export const getSongVisualization = async (songId: number): Promise<VisualizationData> => {
  const token = localStorage.getItem('accessToken');

  const response = await fetch(`${API_BASE}/admins/songs/${songId}/visualization`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('시각화 데이터를 불러오는데 실패했습니다.');
  }

  return response.json();
};
