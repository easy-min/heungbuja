import { useState, useRef, useCallback } from 'react';

interface UseVoiceRecorderReturn {
  isRecording: boolean;
  countdown: number;
  audioBlob: Blob | null;
  error: string | null;
  startRecording: () => Promise<void>;
  stopRecording: () => void;
}

export const useVoiceRecorder = (): UseVoiceRecorderReturn => {
  const [isRecording, setIsRecording] = useState(false);
  const [countdown, setCountdown] = useState(5);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const startRecording = useCallback(async () => {
    try {
      setError(null);
      setAudioBlob(null);
      audioChunksRef.current = [];

      // 마이크 권한 요청
      const stream = await navigator.mediaDevices.getUserMedia({ 
        audio: true 
      });

      // MediaRecorder 생성
      const mimeType = MediaRecorder.isTypeSupported('audio/webm') 
        ? 'audio/webm' 
        : 'audio/mp4';
      
      const mediaRecorder = new MediaRecorder(stream, { 
        mimeType 
      });
      
      mediaRecorderRef.current = mediaRecorder;

      // 녹음 데이터 수집
      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };

      // 녹음 완료 시
      mediaRecorder.onstop = () => {
        const audioBlob = new Blob(audioChunksRef.current, { 
          type: mimeType 
        });
        // console.log('📦 audioBlob 생성:', audioBlob.size, 'bytes');
        setAudioBlob(audioBlob);
        // console.log('✅ setAudioBlob 호출됨');
        setIsRecording(false);
        // console.log('✅ setIsRecording(false) 호출됨');
      };

      // 녹음 시작
      mediaRecorder.start();
      setIsRecording(true);
      setCountdown(5);

      // 5초 카운트다운
      let count = 5;
      const countdownInterval = setInterval(() => {
        count -= 1;
        setCountdown(count);

        if (count <= 0) {
          clearInterval(countdownInterval);
          
          // MediaRecorder 정지
          if (mediaRecorder.state !== 'inactive') {
            mediaRecorder.stop();
          }
          setIsRecording(false);
          
          // 스트림 종료
          stream.getTracks().forEach(track => track.stop());
        }
      }, 1000);
      
      timerRef.current = countdownInterval;

    } catch (err) {
      console.error('녹음 시작 실패:', err);
      setError('마이크 접근 권한이 필요합니다.');
      setIsRecording(false);
    }
  }, []);

  const stopRecording = useCallback(() => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    
    setIsRecording(false);
    
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  return {
    isRecording,
    countdown,
    audioBlob,
    error,
    startRecording,
    stopRecording,
  };
};