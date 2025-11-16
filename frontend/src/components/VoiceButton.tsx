import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useVoiceRecorder } from '../hooks/useVoiceRecorder';
import { useVoiceCommand } from '../hooks/useVoiceCommand';
import VoiceOverlay from './VoiceOverlay';
import { useAudioStore } from '@/store/audioStore';
import { useGameStore } from '@/store/gameStore';
import './VoiceButton.css';

const VoiceButton: React.FC = () => {
  const navigate = useNavigate();
  const {
    isRecording,
    countdown,
    audioBlob,
    startRecording
  } = useVoiceRecorder();

  const {
    isUploading,
    isPlaying,
    responseText,
    response,
    sendCommand,
  } = useVoiceCommand({
    onRetry: () => {
      console.log('🔁 실패 자동 재녹음 시작');
      startRecording();
    },
  });

  const { pause } = useAudioStore();
  const requestGameStop = useGameStore((s) => s.requestStop);

  // Emergency 체크
  const isEmergency = response?.intent === 'EMERGENCY';

  // TTS 재생 상태 추적 (이전 값)
  const prevIsPlayingRef = useRef(false);

  // 수동 녹음(버튼 클릭)으로 시작했는지 추적
  const isManualRecordingRef = useRef(false);
  const emergencyRetryCountRef = useRef(0);
  const autoRetryFlagRef = useRef(false);

  // Emergency 시 TTS 끝나면 자동으로 다시 녹음 (수동 녹음일 때만 1회)
  useEffect(() => {

    // TTS 재생 중이었다가 막 끝난 순간만 감지
    const ttsJustFinished =
      prevIsPlayingRef.current === true &&
      !isPlaying &&
      !isRecording &&
      !isUploading;

    if (
      isManualRecordingRef.current &&
      isEmergency &&
      ttsJustFinished
    ) {
      if (emergencyRetryCountRef.current === 0) {
        // 🔴 첫 번째 응급 인식 후: 재녹음 1회
        console.log('🚨 응급 상황 인식 → 재녹음 1회 실행');
        emergencyRetryCountRef.current = 1;
        startRecording();
      } else {
        // 🔴 두 번째 응급 인식 후: 홈으로 이동
        console.log('🚨 두 번째 응급 인식 → 홈으로 이동');
        isManualRecordingRef.current = false;
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
      startRecording();
    } else {
      console.log('⚠️ 버튼 비활성 상태 (isRecording:', isRecording, 'isUploading:', isUploading, 'isPlaying:', isPlaying, ')');
    }
  };

  // 녹음 완료 시 자동 전송
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
