import React, { useEffect } from 'react';
import { useVoiceRecorder } from '../hooks/useVoiceRecorder';
import { useVoiceCommand } from '../hooks/useVoiceCommand';
import VoiceOverlay from './Voiceoverlay';
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
    sendCommand,
  } = useVoiceCommand();

  // 🔍 디버깅: 상태 변화 추적
  // console.log('🎤 VoiceButton 상태:', {
  //   isRecording,
  //   isUploading,
  //   isPlaying,
  //   조건: isRecording || isUploading || isPlaying,
  //   오버레이표시: (isRecording || isUploading || isPlaying) ? 'YES' : 'NO'
  // });

  const handleClick = () => {
    if (!isRecording && !isUploading && !isPlaying) {
      startRecording();
    }
  };

  // 녹음 완료 시 자동 전송
  useEffect(() => {
    if (audioBlob) {
      console.log('녹음 완료! 서버로 전송 중...');
      sendCommand(audioBlob);
    }
  }, [audioBlob, sendCommand]);

  // 통합 에러 메시지
  // const error = recordError || uploadError; // unused for now

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

        {/* 에러 메시지 */}
        {/* {error && (
          <div className="error-message">{error}</div>
        )} */}
      </div>
    </>
  );
};

export default VoiceButton;
