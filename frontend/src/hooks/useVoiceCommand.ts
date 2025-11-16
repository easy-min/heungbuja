import { useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { sendVoiceCommand } from '@/api/voiceCommandApi';
import type { VoiceCommandResponse } from '@/types/voiceCommand';
import { useAudioStore } from '@/store/audioStore';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

interface UseVoiceCommandReturn {
  isUploading: boolean;
  isPlaying: boolean;
  error: string | null;
  response: VoiceCommandResponse | null;
  responseText: string | null;
  sendCommand: (audioBlob: Blob) => Promise<void>;
}

interface UseVoiceCommandOptions {
  onRetry?: () => void;
}

export const useVoiceCommand = (
  options?: UseVoiceCommandOptions
): UseVoiceCommandReturn => {
  const [isUploading, setIsUploading] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<VoiceCommandResponse | null>(null);
  const [responseText, setResponseText] = useState<string | null>(null);
  const navigate = useNavigate();
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const { pause: pauseAudio, play: playAudio } = useAudioStore();
  const autoRetryRef = useRef(false);

  // TTS 재생 함수
  const playTTS = useCallback((ttsUrl: string | null, onComplete?: () => void) => {
    if (!ttsUrl) {
      // TTS 없으면 1초 대기 후 완료
      setIsPlaying(true);
      setTimeout(() => {
        setIsPlaying(false);
        if (onComplete) onComplete();
      }, 1000);
      return;
    }

    // 이전 오디오 정리
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }

    // TTS 재생 시작
    setIsPlaying(true);

    // URL 타입 구분 (Base64 Data URI / 완전한 URL / 상대 경로)
    let audioUrl: string;

    if (ttsUrl.startsWith('data:audio')) {
      // Base64 Data URI는 그대로 사용
      audioUrl = ttsUrl;
      console.log('TTS 재생: Base64 Data URI');
    } else if (ttsUrl.startsWith('http://') || ttsUrl.startsWith('https://')) {
      // 이미 완전한 URL이면 그대로
      audioUrl = ttsUrl;
      console.log('TTS 재생:', audioUrl);
    } else {
      // 상대 경로면 base URL 붙이기
      const baseUrl = API_BASE.replace('/api', ''); // /api 제거
      audioUrl = `${baseUrl}${ttsUrl}`;
      console.log('TTS 재생:', audioUrl);
    }

    // 새 오디오 재생
    const audio = new Audio(audioUrl);
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

  // intent 기반 통합 명령 처리
  const handleCommand = useCallback((response: VoiceCommandResponse) => {
    const { intent, songInfo, screenTransition } = response;

    console.log('명령 처리 - intent:', intent);

    switch (intent) {
      // 음악 제어
      case 'MUSIC_PAUSE':
        console.log('음악 일시정지');
        pauseAudio();
        break;

      case 'MUSIC_RESUME':
        console.log('음악 재생 재개');
        playAudio();
        break;

      case 'MUSIC_STOP':
        console.log('음악 종료, 홈화면 이동');
        navigate('/home');
        break;
      
      // 화면 전환
      case 'MODE_HOME':
        console.log('홈 화면으로 이동');
        navigate('/home');
        break;

      case 'MODE_LISTENING':
        console.log('감상 모드로 이동');
        navigate('/listening', {
          state: songInfo ? { songInfo, autoPlay: true } : undefined
        });
        break;

      case 'MODE_EXERCISE':
        console.log('체조 모드로 이동');
        navigate('/tutorial', {
          state: screenTransition?.data
        });
        break;

      case 'MODE_EXERCISE_END':
        console.log('체조 종료 - 결과 화면으로 이동');
        navigate('/result', {
          state: screenTransition?.data
        });
        break;

      // 노래 선택 (아티스트, 제목, 랜덤 등)
      case 'SELECT_BY_ARTIST':
      case 'SELECT_BY_TITLE':
      case 'SELECT_RANDOM':
        console.log('노래 선택 → /listening으로 이동', songInfo);
        if (songInfo) {
          navigate('/listening', {
            state: {
              songInfo,
              autoPlay: true,
            },
          });
        }
        break;
      
      // 응급 상황
      case 'EMERGENCY':
          break;

      default:
        console.log('처리되지 않은 intent:', intent);
        navigate('/home');
        // screenTransition이 있으면 targetScreen으로 이동
        // if (screenTransition?.targetScreen) {
        //   console.log('기본 화면 전환:', screenTransition.targetScreen);
        //   navigate(screenTransition.targetScreen, {
        //     state: screenTransition.data,
        //   });
        // }
        break;
    }
  }, [pauseAudio, playAudio, navigate]);

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
        // TTS 재생 후 명령 처리
        playTTS(result.ttsAudioUrl, () => {
          // TTS 재생 완료되면 intent 기반 명령 처리
          handleCommand(result);
        });
      } else {
        // 실패 시 에러 메시지
        setError(result.responseText);
        autoRetryRef.current = true;
        // 실패 안내 TTS 재생 후에 재녹음 시도
        playTTS(result.ttsAudioUrl, () => {
          if (autoRetryRef.current && options?.onRetry) {
            console.log('🔄 명령 실패 → 자동 재녹음 시작');
            autoRetryRef.current = false; // 1회만
            options.onRetry();            // 실제 startRecording 실행
          }
        });
      }

    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '음성 명령 처리 중 오류가 발생했습니다.';
      console.error('음성 명령 전송 실패:', err);
      setError(errorMessage);
    } finally {
      // console.log('✅ setIsUploading(false) 호출됨 (finally)');
      setIsUploading(false);
    }
  }, [playTTS, handleCommand]);

  return {
    isUploading,
    isPlaying,
    error,
    response,
    responseText,
    sendCommand,
  };
};