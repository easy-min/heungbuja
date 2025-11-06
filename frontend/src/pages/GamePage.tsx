import { useRef, useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useCamera } from '@/hooks/useCamera';
import { useFrameCapture } from '@/hooks/useFrameCapture';
import { useMusicMonitor } from '@/hooks/useMusicMonitor';
import { useSegmentUpload } from '@/hooks/useSegmentUpload';
import { generateSessionId } from '@/utils/gameHelpers';
import './GamePage.css';

function GamePage() {
  // URL 파라미터
  const { songId } = useParams<{ songId: string }>();
  const navigate = useNavigate();

  // Refs
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const startTimerRef = useRef<number | null>(null);
  // 상태
  const [isGameStarted, setIsGameStarted] = useState(false);
  const [currentSegment, setCurrentSegment] = useState(0);
  const [sessionId] = useState(() => generateSessionId());
  const [testMode] = useState(true);  // ✅ testMode 설정

  // 카메라 훅
  const { stream, isReady, error, startCamera, stopCamera } = useCamera();

  // 음악 모니터링 훅
  const {
    barGroups,
    currentSegmentIndex,
    isMonitoring,
    loadSongData,
    startMonitoring,
    stopMonitoring,
  } = useMusicMonitor({
    audioRef,
    onSegmentStart: handleSegmentStart,
    onSegmentEnd: handleSegmentEnd,
    onAllComplete: handleAllComplete,
  });

  // 프레임 캡처 훅
  const {
    isCapturing,
    startCapture,
    stopCapture,
  } = useFrameCapture({
    videoRef,
    audioRef,
    canvasRef,
  });

  // 세그먼트 업로드 훅
  const {
    uploadQueue,
    isUploading,
    queueSegmentUpload,
  } = useSegmentUpload({
    sessionId,
    songId: songId || 'test-song',
    musicTitle: '당돌한 여자',
    verse: 1,
    testMode,  // ✅ testMode state 사용
    onUploadSuccess: handleUploadSuccess,
    onUploadError: handleUploadError,
  });

  // 컴포넌트 마운트
  useEffect(() => {
    console.log('🎮 GamePage 마운트');
    console.log('📋 Session ID:', sessionId);
    console.log('🎵 Song ID:', songId);

    // 카메라 시작
    startCamera();

    // JSON 로드
    loadSongData('/당돌한여자_섹션추가.json');

    // ✅ 수정: 언마운트/정리 useEffect 내
    return () => {
      console.log('🎮 GamePage 언마운트');
      if (startTimerRef.current !== null) {
        clearTimeout(startTimerRef.current);
        startTimerRef.current = null;
      }
      stopCamera();
      stopMonitoring();
      if (audioRef.current) audioRef.current.pause();
    };
  }, []);

  // 카메라 스트림 연결
  useEffect(() => {
    if (stream && videoRef.current && !videoRef.current.srcObject) {
      videoRef.current.srcObject = stream;
      console.log('📹 카메라 스트림 연결 완료');
    }
  }, [stream]);

  // 캔버스 크기 설정
  useEffect(() => {
    if (videoRef.current && canvasRef.current) {
      const video = videoRef.current;
      video.addEventListener('loadedmetadata', () => {
        if (canvasRef.current) {
          canvasRef.current.width = video.videoWidth || 320;
          canvasRef.current.height = video.videoHeight || 240;
          console.log(`🎨 Canvas 크기: ${canvasRef.current.width}x${canvasRef.current.height}`);
        }
      });
    }
  }, []);

  // 세그먼트 인덱스 업데이트
  useEffect(() => {
    setCurrentSegment(currentSegmentIndex + 1);
  }, [currentSegmentIndex]);

  // 이벤트 핸들러
  function handleTestStart() {
    if (!audioRef.current || !isReady) {
      console.warn('⚠️  카메라 또는 오디오가 준비되지 않았습니다');
      return;
    }

    console.log('🎬 테스트 시작');
    audioRef.current.play();
    startMonitoring();
    setIsGameStarted(true);
  }

  function handleTestStop() {
    console.log('⏹ 테스트 중지');
    if (audioRef.current) {
      audioRef.current.pause();
    }
    stopMonitoring();
    setIsGameStarted(false);
  }

  // ✅ 수정: 오디오 현재시간 기준으로 예약 호출
  function handleSegmentStart(segmentIndex: number) {
    console.log(`▶️  세그먼트 ${segmentIndex + 1} 시작`);
    const group = barGroups[segmentIndex];
    const audio = audioRef.current;
    if (!group || !audio) return;

    const now = audio.currentTime;
    const preRoll = 0.04; // 40ms 정도 앞당겨 시작해 지터 흡수
    const delayMs = Math.max(0, (group.startTime - now - preRoll) * 1000);

    // 이전 예약이 남아있으면 취소
    if (startTimerRef.current !== null) {
      clearTimeout(startTimerRef.current);
      startTimerRef.current = null;
    }

    startTimerRef.current = window.setTimeout(() => {
      console.log('⏱ 예약 캡처 시작', { delayMs, nowAtFire: audio.currentTime.toFixed(3) });
      startCapture(group.startTime, group.endTime);
      startTimerRef.current = null;
    }, delayMs);
  }

  function handleSegmentEnd(segmentIndex: number, frames: any[]) {
      console.log(`⏹ 세그먼트 ${segmentIndex + 1} 종료`);
      if (startTimerRef.current !== null) {
        clearTimeout(startTimerRef.current);
        startTimerRef.current = null;
      }
    // 캡처 중지 및 프레임 가져오기
    const capturedFrames = stopCapture();
    
    console.log(`📦 세그먼트 ${segmentIndex + 1} 프레임: ${capturedFrames.length}개`);

    // 업로드 큐에 추가
    if (capturedFrames.length > 0) {
      queueSegmentUpload({
        index: segmentIndex,
        frames: capturedFrames,
      });
    } else {
      console.warn(`⚠️  세그먼트 ${segmentIndex + 1}에 프레임이 없습니다`);
    }
  }

  function handleAllComplete() {
    console.log('🎉 모든 세그먼트 완료!');
    setIsGameStarted(false);
    
    // 나중에 결과 페이지로 이동
    // navigate('/result');
  }

  function handleUploadSuccess(segmentIndex: number, response?: any) {
    console.log(`✅ 세그먼트 ${segmentIndex} 업로드 성공`, response);
  }

  function handleUploadError(segmentIndex: number, error: Error) {
    console.error(`❌ 세그먼트 ${segmentIndex} 업로드 실패:`, error);
  }
// useEffect(() => {
//   console.log('🔍 barGroups:', barGroups);
//   if (barGroups.length > 0) {
//     console.log('🔍 세그먼트 1:', barGroups[0]);
//   }
// }, [barGroups]);

// useEffect(() => {
//   if (!audioRef.current || !isGameStarted) return;
  
//   const interval = setInterval(() => {
//     console.log('🎵 음악 시간:', audioRef.current?.currentTime.toFixed(2));
//   }, 1000);
  
//   return () => clearInterval(interval);
// }, [isGameStarted]);
  return (
    <div className="game-page">
      {/* 상단: 좌우 분할 */}
      <div className="video-container">
        {/* 왼쪽: 캐릭터 영상 자리 */}
        <div className="character-section">
          <div className="placeholder">
            <h2>캐릭터 영상 자리</h2>
          </div>
        </div>

        {/* 오른쪽: 카메라 */}
        <div className="camera-section">
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="camera-video"
          />
          <canvas ref={canvasRef} className="capture-canvas" />
          
          {/* 세그먼트 정보 */}
          <div className="segment-info">
            <span className="segment-number">
              세그먼트 {currentSegment}/6
            </span>
            {isCapturing && (
              <span className="capturing-badge">📹 캡처 중</span>
            )}
            {isUploading && (
              <span className="uploading-badge">📤 업로드 중</span>
            )}
          </div>

          {/* 에러 표시 */}
          {error && (
            <div className="error-message">
              ❌ {error}
            </div>
          )}

          {/* 카메라 준비 중 */}
          {!isReady && !error && (
            <div className="loading-message">
              📹 카메라 준비 중...
            </div>
          )}
        </div>
      </div>

      {/* 하단: 가사 자리 */}
      <div className="lyrics-container">
        <div className="placeholder">
          <h3>가사 자리</h3>
        </div>
      </div>

      {/* 오디오 (항상 렌더링, testMode일 때만 보임) */}
      <audio
        ref={audioRef}
        src="/당돌한여자.mp3"
        style={{ display: testMode ? 'block' : 'none' }}
      />

      {/* 테스트용 컨트롤 */}
      {testMode && (
        <div className="test-controls">
          <div className="button-group">
            <button
              onClick={handleTestStart}
              disabled={isGameStarted || !isReady}
              className="btn-start"
            >
              🎬 테스트 시작
            </button>
            <button
              onClick={handleTestStop}
              disabled={!isGameStarted}
              className="btn-stop"
            >
              ⏹ 테스트 중지
            </button>
          </div>

          <div className="debug-info">
            <div>카메라: {isReady ? '✅ 준비' : '⏳ 대기'}</div>
            <div>세그먼트: {barGroups.length}개 로드</div>
            <div>모니터링: {isMonitoring ? '✅ 진행 중' : '⏸ 대기'}</div>
            <div>캡처: {isCapturing ? '✅ 진행 중' : '⏸ 대기'}</div>
            <div>업로드 큐: {uploadQueue.length}개</div>
          </div>
        </div>
      )}
    </div>
  );
}

export default GamePage;