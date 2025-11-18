import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useVoiceRecorder } from '../hooks/useVoiceRecorder';
import { useVoiceCommand } from '../hooks/useVoiceCommand';
import { useRaspberryVoice } from '../hooks/useRaspberryVoice';
import VoiceOverlay from './VoiceOverlay';
import { useAudioStore } from '@/store/audioStore';
import { useGameStore } from '@/store/gameStore';
import { useEnvironmentStore } from '@/store/environmentStore';
import './VoiceButton.css';

const VoiceButton: React.FC = () => {
  const navigate = useNavigate();
  const { isRaspberryPi } = useEnvironmentStore();

  // 웹 환경 - 기존 녹음 훅
  const {
    isRecording: webIsRecording,
    countdown: webCountdown,
    audioBlob,
    startRecording
  } = useVoiceRecorder();

  const autoRetryFlagRef = useRef(false); // 수동 녹음당 1회 자동 재녹음 플래그

  const {
    isUploading,
    isPlaying,
    responseText,
    response,
    sendCommand,
  } = useVoiceCommand({
    onRetry: () => {
      // 실패 시 자동 재녹음: 1번만 허용
      if (!autoRetryFlagRef.current) {
        console.log('❌ 자동 재녹음 기회 없음(이미 사용됨)');
        return;
      }
      console.log('🔁 실패 자동 재녹음 시작');
      autoRetryFlagRef.current = false; // 1회 사용

      if (isRaspberryPi) {
        // 라즈베리파이: 재녹음 요청
        retryRecording();
      } else {
        // 웹: 브라우저 녹음
        startRecording();
      }
    }
  });

  // 라즈베리파이 환경 - SSE 기반 훅 (sendCommand 전달)
  const {
    isWakeWordDetected,
    retryRecording,
  } = useRaspberryVoice({
    enabled: isRaspberryPi,
    sendCommand  // VoiceButton의 sendCommand 전달
  });

  // 환경에 따라 상태 선택
  const isRecording = isRaspberryPi ? isWakeWordDetected : webIsRecording;
  const countdown = isRaspberryPi ? 0 : webCountdown;

  const { pause } = useAudioStore();
  const requestGameStop = useGameStore((s) => s.requestStop);

  // Emergency 체크
  const isEmergency = response?.intent === 'EMERGENCY';

  // TTS 재생 상태 추적 (이전 값)
  const prevIsPlayingRef = useRef(false);

  // 수동 녹음(버튼 클릭)으로 시작했는지 추적
  const isManualRecordingRef = useRef(false);
  const emergencyRetryCountRef = useRef(0);

  // 라즈베리파이: 웨이크워드 감지 시 플래그 리셋
  useEffect(() => {
    if (isRaspberryPi && isWakeWordDetected) {
      console.log('🎤 웨이크워드 감지 → 재녹음 플래그 리셋');
      autoRetryFlagRef.current = true;
      emergencyRetryCountRef.current = 0;
    }
  }, [isRaspberryPi, isWakeWordDetected]);

  // Emergency 시 TTS 끝나면 자동으로 다시 녹음 (1회)
  useEffect(() => {

    // TTS 재생 중이었다가 막 끝난 순간만 감지
    const ttsJustFinished =
      prevIsPlayingRef.current === true &&
      !isPlaying &&
      !isRecording &&
      !isUploading;

    if (isEmergency && ttsJustFinished) {
      if (emergencyRetryCountRef.current === 0) {
        // 🔴 첫 번째 응급 인식 후: 재녹음 1회
        console.log('🚨 응급 상황 인식 → 재녹음 1회 실행');
        emergencyRetryCountRef.current = 1;

        if (isRaspberryPi) {
          // 라즈베리파이: 재녹음 요청
          retryRecording();
        } else {
          // 웹: 브라우저 녹음
          startRecording();
        }
      } else {
        // 🔴 두 번째 응급 인식 후: 홈으로 이동
        console.log('🚨 두 번째 응급 인식 → 홈으로 이동');
        emergencyRetryCountRef.current = 0;
        navigate('/home');
      }
    }

    // 현재 isPlaying 값을 다음 렌더링을 위해 저장
    prevIsPlayingRef.current = isPlaying;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEmergency, isPlaying, isRecording, isUploading]);

  const handleClick = () => {
    console.log('🎤 VoiceButton 클릭됨');
    if (!isRecording && !isUploading && !isPlaying) {
      autoRetryFlagRef.current = true;
      console.log('⏸️ 노래 & 게임 일시정지');
      requestGameStop();
      pause();
      console.log('🎙️ 녹음 시작 (수동)');
      isManualRecordingRef.current = true; // 수동 녹음 플래그 설정
      emergencyRetryCountRef.current = 0;
      autoRetryFlagRef.current = true; // 수동 녹음 시작 시: 자동 재녹음 기회 리셋
      startRecording();
    } else {
      console.log('⚠️ 버튼 비활성 상태 (isRecording:', isRecording, 'isUploading:', isUploading, 'isPlaying:', isPlaying, ')');
    }
  };

  // 녹음 완료 시 자동 전송
  // 웹: 항상 전송
  // 라즈베리파이: 버튼 클릭으로 녹음한 경우에만 전송 (웨이크워드는 useRaspberryVoice에서 처리)
  useEffect(() => {
    if (audioBlob) {
      console.log('녹음 완료! 서버로 전송 중...');
      sendCommand(audioBlob);
    }
  }, [audioBlob, sendCommand]);

  return (
    <>
      {/* 음성 인식 오버레이 - 항상 렌더링 */}
      <VoiceOverlay
        isVisible={isRecording || isUploading || isPlaying}
        countdown={countdown}
        isRecording={isRecording}
        isUploading={isUploading}
        isPlaying={isPlaying}
        responseText={responseText}
        isEmergency={isEmergency}
      />

      {/* 마이크 버튼 */}
      <div className="voice-button-wrapper">
        <button 
          className={`voice-button ${isRecording ? 'recording' : ''} ${isUploading ? 'uploading' : ''}`}
          onClick={handleClick}
          disabled={isRecording || isUploading || isPlaying}
          aria-label="음성 인식"
        >
        
            {/* 기본 - 마이크 아이콘 */}
            <svg 
              className="mic-icon" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              strokeWidth="2"
            >
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
          
        </button>

      </div>
    </>
  );
};

export default VoiceButton;
