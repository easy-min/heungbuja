import { useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { sendVoiceCommand } from '../api/voiceCommandApi';
import type { VoiceCommandResponse } from '../types/voiceCommand';

interface UseVoiceCommandReturn {
  isUploading: boolean;
  isPlaying: boolean;
  error: string | null;
  response: VoiceCommandResponse | null;
  responseText: string | null;
  sendCommand: (audioBlob: Blob) => Promise<void>;
}

export const useVoiceCommand = (): UseVoiceCommandReturn => {
  const [isUploading, setIsUploading] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<VoiceCommandResponse | null>(null);
  const [responseText, setResponseText] = useState<string | null>(null);
  const navigate = useNavigate();
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // TTS 재생 함수
  const playTTS = useCallback((ttsUrl: string, onComplete?: () => void) => {
    if (!ttsUrl) {
      // TTS 없으면 3초 대기 후 완료
      setIsPlaying(true);
      setTimeout(() => {
        setIsPlaying(false);
        if (onComplete) onComplete();
      }, 5000);
      return;
    }

    // 이전 오디오 정리
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }

    // TTS 재생 시작
    setIsPlaying(true);

    // 새 오디오 재생
    const audio = new Audio(`https://heungbuja.site${ttsUrl}`);
    audioRef.current = audio;
    
    // TTS 재생 완료 이벤트
    audio.onended = () => {
      console.log('TTS 재생 완료');
      setIsPlaying(false);
      if (onComplete) onComplete();
    };

    // TTS 재생 에러 처리
    audio.onerror = () => {
      console.error('TTS 재생 실패');
      setIsPlaying(false);
      if (onComplete) onComplete(); // 에러여도 계속 진행
    };

    audio.play().catch((err) => {
      console.error('TTS 재생 시작 실패:', err);
      setIsPlaying(false);
      if (onComplete) onComplete(); // 실패해도 계속 진행
    });
  }, []);

  // 화면 전환 처리
  const handleScreenTransition = useCallback((response: VoiceCommandResponse) => {
    const { screenTransition, responseText } = response;

    // responseText에 "게임" 또는 "체조" 키워드가 있으면 tutorial로 이동
    if (responseText && (responseText.includes('게임') || responseText.includes('체조'))) {
      console.log('게임/체조 키워드 감지 → /tutorial로 이동');
      navigate('/tutorial', {
        state: screenTransition?.data || {},
      });
      return;
    }

    if (!screenTransition) {
      // 화면 전환 없음 (일시정지 등)
      return;
    }

    const { targetScreen, action, data } = screenTransition;

    // 게임 시작의 경우 songId를 URL에 포함
    if (action === 'START_GAME' && data?.songId) {
      const gameUrl = `/game/${data.songId}`;
      navigate(gameUrl, {
        state: data,
      });
      return;
    }

    // 그 외의 경우 targetScreen 그대로 사용
    navigate(targetScreen, {
      state: data,
    });
  }, [navigate]);

  // 음성 명령 전송
  const sendCommand = useCallback(async (audioBlob: Blob) => {
    setIsUploading(true);
    console.log('✅ setIsUploading(true) 호출됨');
    setError(null);
    setResponse(null);
    setResponseText(null);

    try {
      console.log('음성 명령 전송 중...');
      const result = await sendVoiceCommand(audioBlob);
      
      console.log('서버 응답:', result);
      setResponse(result);
      
      // responseText 설정
      setResponseText(result.responseText);

      // 성공 시
      if (result.success) {
        // TTS 재생 후 화면 전환
        playTTS(result.ttsAudioUrl, () => {
          // TTS 재생 완료되면 화면 전환
          handleScreenTransition(result);
        });
      } else {
        // 실패 시 에러 메시지
        setError(result.responseText);
        // 에러 TTS도 재생
        playTTS(result.ttsAudioUrl);
      }

    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '음성 명령 처리 중 오류가 발생했습니다.';
      console.error('음성 명령 전송 실패:', err);
      setError(errorMessage);
    } finally {
      // console.log('✅ setIsUploading(false) 호출됨 (finally)');
      setIsUploading(false);
    }
  }, [playTTS, handleScreenTransition]);

  return {
    isUploading,
    isPlaying,
    error,
    response,
    responseText,
    sendCommand,
  };
};