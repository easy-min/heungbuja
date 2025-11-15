import React, { useEffect, useRef } from 'react';
import { useVoiceRecorder } from '../hooks/useVoiceRecorder';
import { useVoiceCommand } from '../hooks/useVoiceCommand';
import VoiceOverlay from './VoiceOverlay';
import { useAudioStore } from '@/store/audioStore';
import './VoiceButton.css';

const VoiceButton: React.FC = () => {
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
  } = useVoiceCommand();

  const { pause } = useAudioStore();

  // Emergency 체크
  const isEmergency = response?.intent === 'EMERGENCY';

  // TTS 재생 상태 추적 (이전 값)
  const prevIsPlayingRef = useRef(false);

  // 수동 녹음(버튼 클릭)으로 시작했는지 추적
  const isManualRecordingRef = useRef(false);

  // Emergency 시 TTS 끝나면 자동으로 다시 녹음 (수동 녹음일 때만 1회)
  useEffect(() => {

    // 수동 녹음에서 시작한 경우만 자동 재녹음
    // TTS가 재생 중 → 끝난 순간만 감지
    if (isManualRecordingRef.current && isEmergency && prevIsPlayingRef.current === true && !isPlaying && !isRecording && !isUploading) {
      
      // TODO(선미니): 웹소켓/게임 영상 등 게임 리소스 정지 구현

      pause(); // 노래 일시정지
      startRecording();
      isManualRecordingRef.current = false; // 플래그 해제 (다음 자동 녹음에서는 무시)
    }

    // 현재 isPlaying 값을 다음 렌더링을 위해 저장
    prevIsPlayingRef.current = isPlaying;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEmergency, isPlaying, isRecording, isUploading]);

  const handleClick = () => {
    console.log('🎤 VoiceButton 클릭됨');
    if (!isRecording && !isUploading && !isPlaying) {
      // 녹음 시작 전에 노래 멈추기
      console.log('⏸️ 노래 일시정지 시도');
      pause();
      console.log('🎙️ 녹음 시작 (수동)');
      isManualRecordingRef.current = true; // 수동 녹음 플래그 설정
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
