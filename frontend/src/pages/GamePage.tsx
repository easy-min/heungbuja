import { useRef, useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useCamera } from '@/hooks/useCamera';
import { useFrameCapture } from '@/hooks/useFrameCapture';
import { useMusicMonitor } from '@/hooks/useMusicMonitor';
import { useLyricsSync } from '@/hooks/useLyricsSync';
import { useSegmentUpload } from '@/hooks/useSegmentUpload';
import { generateSessionId } from '@/utils/gameHelpers';
import { type UploadResponse, type LyricLine } from '@/types';
import './GamePage.css';
const BASE_URL = import.meta.env.BASE_URL;

function GamePage() {

  function switchSectionVideo(next: SectionKey) {
    const mv = motionVideoRef.current;
    const au = audioRef.current;
    if (!mv) return;

    // 같은 섹션이면 스킵
    if (currentSectionRef.current === next) return;
    currentSectionRef.current = next;

    const { src, bpm: videoBpm } = VIDEO_META[next];

    // 현재 재생중인지 보관
    const shouldPlay = !mv.paused;

    // 소스 갈아끼우고 로드
    mv.src = src;
    mv.load();

    // 메타 로드 후 배속 반영 + 재생
    const applyAndPlay = async () => {
        // 오디오 BPM 대비 영상 배속
        const songBpm = songBpmRef.current || 120;
        mv.playbackRate = songBpm / videoBpm;

        mv.currentTime = LOOP_RESTART;

        if (shouldPlay || (au && !au.paused)) {
          await mv.play().catch(() => {});
        }
    };

    if (mv.readyState < 2) {
      const onCanPlay = () => {
        mv.removeEventListener('canplay', onCanPlay);
        applyAndPlay();
      };
      mv.addEventListener('canplay', onCanPlay, { once: true });
    } else {
      void applyAndPlay();
    }
  }

  // === 섹션별 메타 (영상 BPM/루프 박자 수) ===
  const VIDEO_META = {
    intro: { src: `${BASE_URL}break.mp4`, bpm: 100, loopBeats: 8 },
    break: { src: `${BASE_URL}break.mp4`, bpm: 100, loopBeats: 8 },
    part1: { src: `${BASE_URL}part1.mp4`, bpm: 98.5, loopBeats: 16 },
    part2: { src: `${BASE_URL}part2.mp4`, bpm: 99, loopBeats: 16 },
  } as const;

  type SectionKey = keyof typeof VIDEO_META;

  // === BPM, 싱크 상태 Ref ===
  const songBpmRef = useRef<number>(120); // JSON에서 갱신
  const currentSectionRef = useRef<SectionKey>('break');

  // 수동 루프용
  const LOOP_EPS = 0.02;          // 끝 경계 여유 (초) - 10~30ms 권장
  const LOOP_RESTART = 0.005;     // 되감을 위치 (초)

  /** 현재 섹션 루프 길이(초) */
  const getLoopLenSec = (section: SectionKey) => {
    const { bpm, loopBeats } = VIDEO_META[section];
    return (60 / bpm) * loopBeats;
  };

  // URL 파라미터 및 state
  const { songId } = useParams<{ songId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const gameData = location.state as any; // 음성 명령으로 전달받은 게임 데이터

  // Refs
  const motionVideoRef = useRef<HTMLVideoElement | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const startTimerRef = useRef<number | null>(null);
  const [isCounting, setIsCounting] = useState(false);
  const [count, setCount] = useState(5);
  const countdownTimerRef = useRef<number | null>(null);
  const hasNavigatedRef = useRef(false);

  // 상태
  const [isGameStarted, setIsGameStarted] = useState(false);
  const [currentSegment, setCurrentSegment] = useState(0);
  // sessionId: 음성 명령으로 받은 데이터 우선, 없으면 생성
  const [sessionId] = useState(() => gameData?.sessionId || generateSessionId());
  const [testMode] = useState(false);  // ✅ testMode 설정

  // 가사
  const [lyrics, setLyrics] = useState<LyricLine[]>([]);
  const { current: currentLyric, next: nextLyric, isInstrumental } =
  useLyricsSync(audioRef, lyrics, { prerollSec: 0.04 });


  // 카메라 훅
  const { stream, isReady, error, startCamera, stopCamera } = useCamera();

  // 음악 모니터링 훅
  const {
    barGroups,
    currentSegmentIndex,
    isMonitoring,
    songBpm,
    // sectionTimes,
    loadSongData,
    startMonitoring,
    stopMonitoring,
  } = useMusicMonitor({
    audioRef,
    onSegmentStart: handleSegmentStart,
    onSegmentEnd: handleSegmentEnd,
    onAllComplete: handleAllComplete,
    onSectionEnter: (label) => {
      const map: Record<string, SectionKey> = {
        intro: 'break',
        break: 'break',
        part1: 'part1',
        part2: 'part2',
      };
      switchSectionVideo(map[label] ?? 'break');
    },
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

  // 자동 카운트다운
  useEffect(() => {
    if (isReady && !isGameStarted && !isCounting) {
      startCountdown();
    }
  }, [isReady]);

  // 컴포넌트 마운트
  useEffect(() => {
    console.log('🎮 GamePage 마운트');
    console.log('📋 Session ID:', sessionId);
    console.log('🎵 Song ID:', songId);

    // 카메라 시작
    startCamera();

    // JSON 로드
    loadSongData(`${BASE_URL}당돌한여자.json`);

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

      if (countdownTimerRef.current !== null) {
        clearInterval(countdownTimerRef.current);
        countdownTimerRef.current = null;
      }
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

  // 노래 bpm 업데이트
  useEffect(() => {
    if (songBpm) songBpmRef.current = songBpm;
  }, [songBpm]);

  // 세그먼트 인덱스 업데이트
  useEffect(() => {
    setCurrentSegment(currentSegmentIndex + 1);
  }, [currentSegmentIndex]);

  // 가사 업데이트
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(`${BASE_URL}당돌한여자_가사.json`);
        const data: { lines: LyricLine[] } = await res.json();
        if (!cancelled) setLyrics(data.lines ?? []);
      } catch (e) {
        console.warn('가사 로드 실패', e);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  // 오디오 끝나면 게임 종료
  useEffect(() => {
    const audio = audioRef.current;
    const mv = motionVideoRef.current;
    if (!audio || !mv) return;

    const handleEnded = () => {
      console.log('🎵 노래 재생 완료 → 영상 정지');
      mv.pause();
      mv.currentTime = 0;
      goToResultOnce();
    };

    audio.addEventListener('ended', handleEnded);
    return () => {
      audio.removeEventListener('ended', handleEnded);
    };
  }, []);

  // 수동 루프
  useEffect(() => {
    const mv = motionVideoRef.current;
    if (!mv) return;

    let raf = 0;

    const tick = () => {
      raf = requestAnimationFrame(tick);
      if (mv.readyState < 1) return; // 메타데이터 아직 X

      // 이론 루프 길이(섹션 bpm & loopBeats) vs 실제 소스 duration 중 작은 값 사용
      const nominal = getLoopLenSec(currentSectionRef.current);
      const dur = Number.isFinite(mv.duration) ? mv.duration : nominal;
      const loopEnd = Math.min(nominal, dur);

      if (mv.currentTime >= loopEnd - LOOP_EPS) {
        mv.currentTime = LOOP_RESTART;
        if (mv.paused) { mv.play().catch(() => {}); }
      }
    };

    // 혹시 duration이 더 짧아 실제로 ended가 발생해도 복구
    const onEnded = () => {
      mv.currentTime = LOOP_RESTART;
      mv.play().catch(() => {});
    };

    mv.addEventListener('ended', onEnded);
    raf = requestAnimationFrame(tick);

    return () => {
      mv.removeEventListener('ended', onEnded);
      cancelAnimationFrame(raf);
    };
  }, []);

  // 카운트 다운
  function startCountdown() {
    if (isGameStarted || isCounting) return;

    setIsCounting(true);
    setCount(5);

    countdownTimerRef.current = window.setInterval(() => {
      setCount((prev) => {
        const next = prev - 1;
        if (next <= 0) {
          if (countdownTimerRef.current !== null) {
            clearInterval(countdownTimerRef.current);
            countdownTimerRef.current = null;
          }
          setIsCounting(false);
          void beginGame(); // 카운트다운이 끝나면 실제 시작
          return 0;
        }
        return next;
      });
    }, 1000);
  }

  // 게임 마무리하고 결과 페이지로 이동
  function goToResultOnce() {
    if (hasNavigatedRef.current) return;
    hasNavigatedRef.current = true;

    try {
      stopMonitoring();
      if (audioRef.current) audioRef.current.pause();
      stopCamera();
      setIsGameStarted(false);
    } finally {
      navigate('/result');
    }
  }

  // 이벤트 핸들러
  async function beginGame() {
    if (!audioRef.current || !isReady) {
      console.warn('⚠️  카메라 또는 오디오가 준비되지 않았습니다');
      return;
    }

    switchSectionVideo('break');

    console.log('🎬 테스트 시작');
    const currentSection = currentSectionRef.current;
    const sectionVideoBpm = VIDEO_META[currentSection].bpm;
    const mv = motionVideoRef.current;

    await audioRef.current.play().catch(e => console.warn('audio play err', e));

    if (mv) {
      if (mv.readyState < 1) {
        await new Promise<void>((resolve) => {
          const onMeta = () => { mv.removeEventListener('loadedmetadata', onMeta); resolve(); };
          mv.addEventListener('loadedmetadata', onMeta, { once: true });
        });
      }
      mv.currentTime = 0;
      mv.playbackRate = songBpm / sectionVideoBpm;
      await mv.play().catch(e => console.warn('video play err', e));
    } else {
      console.warn('⚠️ motionVideoRef 없음');
    }

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

    if (countdownTimerRef.current !== null) {
      clearInterval(countdownTimerRef.current);
      countdownTimerRef.current = null;
    }
    setIsCounting(false);
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

  function handleSegmentEnd(segmentIndex: number) {
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
  }

  function handleUploadSuccess(segmentIndex: number, response?: UploadResponse) {
    console.log(`✅ 세그먼트 ${segmentIndex} 업로드 성공`, response);
  }

  function handleUploadError(segmentIndex: number, error: Error) {
    console.error(`❌ 세그먼트 ${segmentIndex} 업로드 실패:`, error);
  }

  return (
    <>
    {isCounting && (
      <div className="countdown-overlay">
        <div className="countdown-bubble">
          {count > 0 ? count : 'Go!'}
        </div>
      </div>
    )}
    <div className="game-page">
      {/* 좌측: 동작 시연 및 가사 */}
      <div className="video-container">
        {/* 위쪽: 캐릭터 영상 자리 */}
        <div className="character-section">
          <video
            ref={motionVideoRef}
            id="motion"
            // loop
            preload="auto"
            muted
            playsInline
            src={`${BASE_URL}break.mp4`}
            className="motion-video"
            style={{width: '800px'}}
          />
        </div>
        {/* 아래쪽: 가사 자리 */}
        <div className="lyrics-container">
          {/* 오디오 (항상 렌더링, testMode일 때만 보임) */}
          <audio
            controls
            ref={audioRef}
            src={`${BASE_URL}당돌한여자.mp3`}
            style={{ display: testMode ? 'block' : 'none', width: '40%', height: '20%' }}
          />

          {/* === 가사 표시 === */}
          <div className="lyrics-display">
            <div className="lyrics-current">
              {isInstrumental
                ? '(간주 중)'
                : currentLyric?.text ?? '\u00A0'}
            </div>
            <div className="lyrics-next">
              {!isInstrumental
              ? nextLyric?.text ?? '\u00A0'
              : '\u00A0'}
            </div>
          </div>
        </div>       
      </div>

      {/* 우측: 카메라 촬영 및 피드백 */}
      <div className="camera-container">
        {/* 위쪽: 카메라 */}
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
              {/* 세그먼트 {currentSegment}/6 */}
              사용자 화면
            </span>
            {isCapturing && (
              <span className="capturing-badge">📹 동작 인식 중</span>
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

        {/* 아래쪽: 피드백 */}
        {/* <div className="feedback-section"> */}
          {/* 테스트용 컨트롤 */}
          {/* {testMode && (
            <div className="test-controls">
              <div className="button-group">
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
          </div> */}
        </div>
      </div>
    </>
  );
}

export default GamePage;
