import type { VoiceCommandResponse } from '../types/voiceCommand';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export const sendVoiceCommand = async (audioBlob: Blob): Promise<VoiceCommandResponse> => {
  // 토큰 가져오기 (localStorage에서)
  const token = localStorage.getItem('userAccessToken');
  
  if (!token) {
    throw new Error('인증 토큰이 없습니다. 다시 로그인해주세요.');
  }

  // Blob을 File로 변환
  const audioFile = new File([audioBlob], 'voice-command.webm', {
    type: audioBlob.type,
  });

  // FormData 생성
  const formData = new FormData();
  formData.append('audioFile', audioFile);

  try {
    const response = await fetch(`${API_BASE}/commands/process`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        // Content-Type은 설정하지 않음 (FormData가 자동 처리)
      },
      body: formData,
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error('인증이 만료되었습니다. 다시 로그인해주세요.');
      }
      if (response.status === 400) {
        throw new Error('음성 파일 형식이 올바르지 않습니다.');
      }
      throw new Error(`서버 오류: ${response.status}`);
    }

    const data: VoiceCommandResponse = await response.json();
    return data;

  } catch (error) {
    if (error instanceof Error) {
      throw error;
    }
    throw new Error('음성 명령 전송에 실패했습니다.');
  }
};