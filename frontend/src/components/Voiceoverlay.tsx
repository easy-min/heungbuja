import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import './VoiceOverlay.css';

interface VoiceOverlayProps {
  isVisible: boolean;
  countdown: number;
  isRecording: boolean;
  isUploading: boolean;
  isPlaying: boolean;
  responseText?: string | null;
}

const VoiceOverlay: React.FC<VoiceOverlayProps> = ({ 
  isVisible,
  countdown, 
  isRecording, 
  isUploading, 
  isPlaying, 
  responseText 
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
  
  // 첫 렌더링 체크 (방법 2)
  const isFirstRender = useRef(true);
  // 이전 텍스트 추적 (방법 3)
  const prevTextRef = useRef(displayText);

  // 텍스트 변경 시 애니메이션 적용
  useEffect(() => {
    const newText = getDisplayText();
    
    // 첫 렌더링은 애니메이션 없이 (방법 2)
    if (isFirstRender.current) {
      isFirstRender.current = false;
      setDisplayText(newText);
      prevTextRef.current = newText;
      return;
    }
    
    // 실제로 텍스트가 바뀔 때만 애니메이션 (방법 3)
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
    <div className={`voice-overlay ${isVisible ? 'visible' : ''}`}>
      <div className="voice-overlay-content">
        {/* 단일 요소로 텍스트 표시 (애니메이션으로 부드럽게) */}
        <p className={`voice-overlay-title ${isFading ? 'fading' : ''}`}>
          {displayText}
        </p>
        
        <div className="voice-circle-container">
          {/* 회전하는 그라디언트 레이어 */}
          <div className="glow-layer"></div>
          
          {/* 중앙 흰색 원 */}
          <div className="voice-circle">
            {isUploading ? (
              <div className="voice-circle-text">인식 중...</div>
            ) : isPlaying ? (
              null
            ) : (
              <div className="voice-circle-countdown">{countdown}</div>
            )}
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
};

export default VoiceOverlay;