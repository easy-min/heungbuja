import { useRef, useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useCamera } from '@/hooks/useCamera';
import { useFrameStreamer } from '@/hooks/useFrameStreamer';
import { useMusicMonitor } from '@/hooks/useMusicMonitor';
import { useLyricsSync } from '@/hooks/useLyricsSync';
import { useWs } from '@/hooks/useWs';
import { type LyricLine } from '@/types/song';
import { gameStartApi } from '@/api/game';
import { useGameStore } from '@/store/gameStore';
import { GAME_CONFIG } from '@/utils/constants';
import './GamePage.css';

function GamePage() {
  // === WS + Streamer ===
  const { send } = useWs(import.meta.env.VITE_WS_URL);

  // === 상태 / 참조 ===
  const { songId } = useParams<{ songId: string }>();
  const motionVideoRef = useRef<HTMLVideoElement | null>(null); // 동작 영상
  const videoRef = useRef<HTMLVideoElement | null>(null); //카메라 영상
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const captureTimeoutsRef = useRef<number[]>([]);
  const countdownTimerRef = useRef<number | null>(null);
  const hasNavigatedRef = useRef(false);
  const songBpmRef = useRef<number>(120);
  const currentSectionRef = useRef<'intro' | 'break' | 'verse1' | 'verse2'>('break');
  const navigate = useNavigate();

  const [isCounting, setIsCounting] = useState(false);
  const [count, setCount] = useState(5);
  const [isGameStarted, setIsGameStarted] = useState(false);
  const [lyrics, setLyrics] = useState<LyricLine[]>([]);
  
  const { isCapturing, start: startStream, stop: stopStream } = useFrameStreamer({
    videoRef, audioRef, canvasRef,
  });
  const { stream, isReady, error, startCamera, stopCamera } = useCamera();
  const { setAll } = useGameStore();
  const { current: currentLyric, next: nextLyric, isInstrumental } =
    useLyricsSync(audioRef, lyrics, { prerollSec: 0.04 });

  // === 모니터링 (섹션 감지 → 영상 전환) ===
  const { loadFromGameStart, startMonitoring, stopMonitoring } = useMusicMonitor({
    audioRef,
    onSectionEnter: (label) => {
      const map = { intro: 'break', break: 'break', verse1: 'verse1', verse2: 'verse2' } as const;
      switchSectionVideo(map[label]);
    },
  });

  // === 영상 메타 ===
  const VIDEO_META = {
    intro:  { src: '/break.mp4', bpm: 100,  loopBeats: 8  },
    break:  { src: '/break.mp4', bpm: 100,  loopBeats: 8  },
    verse1: { src: '/part1.mp4', bpm: 98.5, loopBeats: 16 },
    verse2: { src: '/part2.mp4', bpm: 99,   loopBeats: 16 },
  } as const;
  type SectionKey = keyof typeof VIDEO_META;

  // === 수동 루프 파라미터 ===
  const LOOP_EPS = 0.03;     // 경계 여유
  const LOOP_RESTART = 0.05; // 되감을 위치(싱크 보정)

  const getLoopLenSec = (section: SectionKey) => {
    const { bpm, loopBeats } = VIDEO_META[section];
    return (60 / bpm) * loopBeats;
  };

  // === 자동 카운트다운 ===
  useEffect(() => {
    const readyToStart = !!(isReady && audioRef.current?.src);
    if (readyToStart && !isGameStarted && !isCounting && !countdownTimerRef.current) {
      startCountdown();
    }
  }, [isReady, isGameStarted, isCounting]);

  // 노래 끝 → 결과로
  useEffect(() => {
    const audio = audioRef.current;
    const mv = motionVideoRef.current;
    if (!audio || !mv) return;

    const handleEnded = () => {
      mv.pause();
      mv.currentTime = 0;
      goToResultOnce();
    };

    audio.addEventListener('ended', handleEnded);
    return () => {
      audio.removeEventListener('ended', handleEnded);
    };
  }, []);

  // === 섹션별 영상 전환 ===
  function switchSectionVideo(next: SectionKey) {
    const mv = motionVideoRef.current;
    const au = audioRef.current;
    if (!mv) return;

    currentSectionRef.current = next;

    const { src, bpm: videoBpm } = VIDEO_META[next];
    const shouldPlayNow = !!au && !au.paused;
    const needSrcSwap = !mv.src.endsWith(src);

    const applyAndPlay = async () => {
      const songBpm = songBpmRef.current || 120;
      mv.loop = false;
      mv.pause(); // 소스 교체 직후 잔여 재생 방지
      mv.playbackRate = songBpm / videoBpm;
      mv.currentTime = LOOP_RESTART;
      if (shouldPlayNow) await mv.play().catch(() => {});
    };

    if (needSrcSwap) {
      mv.src = src;
      mv.load();
      const onReady = () => { applyAndPlay(); mv.removeEventListener('loadedmetadata', onReady); };
      mv.addEventListener('loadedmetadata', onReady, { once: true });
    } else {
      void applyAndPlay();
    }

    if (needSrcSwap) {
      mv.src = src;
      mv.load();
      if (mv.readyState < 2) {
        mv.addEventListener('canplay', applyAndPlay, { once: true });
      } else {
        void applyAndPlay();
      }
    } else {
      void applyAndPlay();
    }
  }

  // === 수동 루프 러너(한 번만 설치) ===
  useEffect(() => {
    const mv = motionVideoRef.current;
    if (!mv) return;

    let raf = 0;
    const tick = () => {
      raf = requestAnimationFrame(tick);
      if (mv.readyState < 2) return;

      const nominal = getLoopLenSec(currentSectionRef.current);
      const dur = Number.isFinite(mv.duration) ? mv.duration : nominal;
      const loopEnd = Math.min(nominal, dur);

      if (mv.currentTime >= loopEnd - LOOP_EPS) {
        mv.currentTime = LOOP_RESTART;
        if (mv.paused) { mv.play().catch(() => {}); }
      }
    };

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

  // === 게임 시작 ===
  async function beginGame() {
    if (!audioRef.current || !isReady) return;
    startMonitoring();

    // 오디오 먼저 재생
    await audioRef.current.play().catch(e => console.warn('audio play err', e));

    scheduleRangeCaptures(); // 구간 캡처/스트리밍 시작
    setIsGameStarted(true);
  }

  // === 구간 캡처 스케줄링(서버 segments 사용) ===
  function scheduleRangeCaptures() {
    const audio = audioRef.current;
    const store = useGameStore.getState();
    const segs = useGameStore.getState().segments;
    if (!audio || !segs) return;

    clearCaptureTimeouts();

    const sessionId = store.sessionId!;
    const songTitle = store.songInfo?.title ?? 'unknown';
    const segments = [
      { key: 'verse1' as const, start: segs.verse1.startTime, end: segs.verse1.endTime },
      { key: 'verse2' as const, start: segs.verse2.startTime, end: segs.verse2.endTime },
    ];

    segments.forEach(({ key, start, end }) => {
      // ② 현재 시각 기준 지연 계산(음악이 이미 시작되어 있을 수 있음)
      const now = audio.currentTime;
      const delayMs = Math.max(0, (start - now) * 1000);

      const timeoutId = window.setTimeout(() => {
        // ③ 콜백 진입 시점에 다시 현재 시간을 확인(시킹/백그라운드 지연 대비)
        const cur = audio.currentTime;

        // 이미 구간이 끝났으면 실행하지 않음
        if (cur >= end) return;

        // 중간부터라도 시작: start가 지났다면 cur부터 캡처 시작
        const effectiveStart = Math.max(cur, start);

        startStream(effectiveStart, end, (blob, { t, idx }) => {
          send(blob, {
            sessionId,
            songTitle,
            section: key,
            frameIndex: idx,
            musicTime: Number(t.toFixed(3)),
            fps: GAME_CONFIG.FPS,
          });
        });
      }, delayMs);

      // ④ 타이머 ID 저장(나중에 일괄 해제)
      captureTimeoutsRef.current.push(timeoutId);
    });
  }

  function clearCaptureTimeouts() {
    captureTimeoutsRef.current.forEach(id => clearTimeout(id));
    captureTimeoutsRef.current = [];
  }

  // === 카운트다운 ===
  function startCountdown() {
    if (isGameStarted || isCounting) return;
    setIsCounting(true);
    setCount(5);

    countdownTimerRef.current = window.setInterval(() => {
      setCount((prev) => {
        const next = prev - 1;
        if (next <= 0) {
          clearInterval(countdownTimerRef.current!);
          countdownTimerRef.current = null;
          setIsCounting(false);
          setIsGameStarted(true);
          void beginGame();
          return 0;
        }
        return next;
      });
    }, 1000);
  }

  // === 종료 시 결과 페이지 이동 ===
  function goToResultOnce() {
    if (hasNavigatedRef.current) return;
    hasNavigatedRef.current = true;

    stopMonitoring();
    stopCamera();
    stopStream();
    clearCaptureTimeouts();
    if (audioRef.current) audioRef.current.pause();

    navigate('/result');
  }

  // === 초기화 ===
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        startCamera();

        const id = Number(songId) || 1;
        const res = await gameStartApi(id);
        if (cancelled) return;

        const { sessionId, songInfo, timeline, lyrics, videoUrls, segments } = res.data;
        setAll({ sessionId, songInfo, timeline, lyrics, videoUrls, segments });

        if (audioRef.current) {
          audioRef.current.src = songInfo.audioUrl;
          audioRef.current.load();
        }

        setLyrics(lyrics ?? []);
        songBpmRef.current = songInfo.bpm;

        await loadFromGameStart({
          bpm: songInfo.bpm,
          duration: songInfo.duration,
          timeline,
        });
      } catch (e) {
        console.error('게임 시작 초기화 실패:', e);
      }
    })();

    return () => {
      cancelled = true;
      stopCamera();
      stopMonitoring();
      stopStream();
      clearCaptureTimeouts();
      if (audioRef.current) audioRef.current.pause();
    };
  }, [songId]);

  // === 카메라 스트림 연결 ===
  useEffect(() => {
    if (stream && videoRef.current && !videoRef.current.srcObject) {
      videoRef.current.srcObject = stream;
      console.log('📹 카메라 스트림 연결 완료');
    }
  }, [stream]);

  // === Canvas 크기 ===
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !canvasRef.current) return;

    const onMeta = () => {
      if (!canvasRef.current) return;
      canvasRef.current.width = video.videoWidth || 320;
      canvasRef.current.height = video.videoHeight || 240;
    };
    video.addEventListener('loadedmetadata', onMeta);
    return () => video.removeEventListener('loadedmetadata', onMeta);
  }, []);

  return (
    <>
      {isCounting && (
        <div className="countdown-overlay">
          <div className="countdown-bubble">{count > 0 ? count : 'Go!'}</div>
        </div>
      )}
      <div className="game-page">
        <div className="video-container">
          <div className="character-section">
            <video
              ref={motionVideoRef}
              preload="auto"
              muted
              playsInline
              src="/break.mp4"
              className="motion-video"
              style={{ width: '800px' }}
            />
          </div>
          <div className="lyrics-container">
            <audio controls ref={audioRef} style={{ display: 'block', width: '40%', height: '20%' }} />
            <div className="lyrics-display">
              <div className="lyrics-current">{isInstrumental ? '(간주 중)' : currentLyric?.text ?? '\u00A0'}</div>
              <div className="lyrics-next">{!isInstrumental ? nextLyric?.text ?? '\u00A0' : '\u00A0'}</div>
            </div>
          </div>
        </div>

        <div className="camera-container">
          <div className="camera-section">
            <video ref={videoRef} autoPlay playsInline muted className="camera-video" />
            <canvas ref={canvasRef} className="capture-canvas" />

            <div className="segment-info">
              {isCapturing && <span className="capturing-badge">📹 캡처 중</span>}
            </div>

            {error && <div className="error-message">❌ {error}</div>}
            {!isReady && !error && <div className="loading-message">📹 카메라 준비 중...</div>}
          </div>
        </div>
      </div>
    </>
  );
}

export default GamePage;
