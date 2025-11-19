import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import MicIcon from './icons/MicIcon';
import SpeakerIcon from './icons/SpeakerIcon';
import LoadingDots from './icons/LoadingDots';
import './VoiceOverlay.css';

interface VoiceOverlayProps {
  isVisible: boolean;
  countdown: number;
  isRecording: boolean;
  isUploading: boolean;
  isPlaying: boolean;
  responseText?: string | null;
  isEmergency?: boolean;
}

const VoiceOverlay: React.FC<VoiceOverlayProps> = ({
  isVisible,
  countdown,
  isRecording,
  isUploading,
  isPlaying,
  responseText,
  isEmergency = false
}) => {
  // 표시할 텍스트 결정
  const getDisplayText = () => {
    if (isRecording) return "네, 말씀해주세요";
    if (isUploading) return "잠시만 기다려주세요...";
    if (responseText) return responseText;
    return "네, 말씀해주세요";
  };

  const [displayText, setDisplayText] = useState(getDisplayText());
  const [isFading, setIsFading] = useState(false);

  // 첫 렌더링 체크
  const isFirstRender = useRef(true);
  // 이전 텍스트 추적
  const prevTextRef = useRef(displayText);

  // 텍스트 변경 시 애니메이션 적용
  useEffect(() => {
    const newText = getDisplayText();

    // 첫 렌더링은 애니메이션 없이
    if (isFirstRender.current) {
      isFirstRender.current = false;
      setDisplayText(newText);
      prevTextRef.current = newText;
      return;
    }

    // 실제로 텍스트가 바뀔 때만 애니메이션
    if (newText !== prevTextRef.current) {
      // Fade out
      setIsFading(true);
      
      setTimeout(() => {
        // 텍스트 변경
        setDisplayText(newText);
        prevTextRef.current = newText;
        // Fade in
        setIsFading(false);
      }, 150);
    }
  }, [isRecording, isUploading, responseText]);

  return createPortal(
    <div className={`voice-overlay ${isVisible ? 'visible' : ''} ${isEmergency ? 'emergency' : ''}`}>
      <div className="voice-overlay-content">
        {/* 단일 요소로 텍스트 표시 (애니메이션으로 부드럽게) */}
        <p className={`voice-overlay-title ${isFading ? 'fading' : ''}`}>
          {displayText}
        </p>
        
        <div className="voice-circle-container">
          {/* 회전하는 그라디언트 레이어 */}
          {/* <div className="glow-layer"></div> */}

          {/* 녹음 중일 때만 카운트다운을 원 위에 표시 */}
          {isRecording && countdown > 0 && (
            <div className="voice-countdown-external">{countdown}</div>
          )}

          {/* 중앙 원 - 상태별 클래스 적용 */}
          <div className={`voice-circle ${isRecording ? 'recording' : ''} ${isUploading ? 'uploading' : ''} ${isPlaying ? 'playing' : ''}`}>
            {/* 녹음 중 - 마이크만 */}
            {isRecording && (
              <MicIcon className="voice-icon" size={80} />
            )}

            {/* 인식 중 - 점 3개 */}
            {isUploading && (
              <LoadingDots className="voice-loading" />
            )}

            {/* TTS 재생 중 - 스피커 */}
            {isPlaying && (
              <SpeakerIcon className="voice-icon" size={80} />
            )}
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
};

export default VoiceOverlay;