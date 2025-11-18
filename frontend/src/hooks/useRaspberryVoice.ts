import { useEffect, useRef, useState } from 'react';
import type { VoiceCommandResponse } from '@/types/voiceCommand';

const LOCAL_SERVER_URL = 'http://localhost:3001';

interface UseRaspberryVoiceOptions {
  enabled: boolean; // 라즈베리파이일 때만 true
  sendCommand: (audioBlob: Blob) => Promise<void>; // VoiceButton에서 전달받음
  onCommandResult?: (result: VoiceCommandResponse) => void; // SSE로 받은 결과 처리
}

export const useRaspberryVoice = ({ enabled, sendCommand, onCommandResult }: UseRaspberryVoiceOptions) => {
  const [isWakeWordDetected, setIsWakeWordDetected] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled) return;

    console.log('🔗 라즈베리파이 모드: SSE 연결 시작');

    // SSE 연결
    const eventSource = new EventSource(`${LOCAL_SERVER_URL}/api/voice-events`);
    eventSourceRef.current = eventSource;

    // 연결 성공
    eventSource.onopen = () => {
      console.log('✅ SSE 연결 성공');
    };

    // 메시지 수신
    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        console.log('📩 SSE 이벤트 수신:', data);

        switch (data.type) {
          case 'CONNECTED':
            console.log('🔗 SSE 연결 확인');
            break;

          case 'WAKE_WORD_DETECTED':
            console.log('🎤 웨이크워드 감지됨! VoiceOverlay 표시');
            setIsWakeWordDetected(true);
            // 7초 후 자동으로 오버레이 닫기 (띠링 소리 2초 + 녹음 5초)
            setTimeout(() => {
              console.log('⏰ 웨이크워드 오버레이 자동 종료');
              setIsWakeWordDetected(false);
            }, 7000);
            break;

          case 'COMMAND_RESULT':
            console.log('🎯 음성 명령 결과 수신:', data.payload);
            if (onCommandResult && data.payload) {
              onCommandResult(data.payload);
            }
            break;

          default:
            console.log('❓ 알 수 없는 이벤트:', data.type);
        }
      } catch (err) {
        console.error('❌ SSE 메시지 파싱 실패:', err);
      }
    };

    // 에러 처리
    eventSource.onerror = (error) => {
      console.error('❌ SSE 연결 에러:', error);
      // 재연결은 브라우저가 자동으로 시도
    };

    // 정리
    return () => {
      console.log('🔌 SSE 연결 종료');
      eventSource.close();
    };
  }, [enabled]);

  // B안: main.py가 직접 백엔드로 전송하므로 파일 다운로드 로직 제거
  // 오버레이만 표시하면 됨

  // 재녹음 요청 함수
  const retryRecording = async () => {
    try {
      console.log('🔁 재녹음 요청 전송 중...');
      await fetch(`${LOCAL_SERVER_URL}/api/retry-recording`, {
        method: 'POST'
      });
      console.log('✅ 재녹음 요청 전송 완료');
    } catch (err) {
      console.error('❌ 재녹음 요청 실패:', err);
    }
  };

  return {
    isWakeWordDetected,
    retryRecording,
  };
};
