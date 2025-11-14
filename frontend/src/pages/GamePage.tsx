import { useRef, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCamera } from '@/hooks/useCamera';
import { useFrameStreamer } from '@/hooks/useFrameStreamer';
import { useMusicMonitor } from '@/hooks/useMusicMonitor';
import { useLyricsSync } from '@/hooks/useLyricsSync';
import { useGameWs } from '@/hooks/useGameWs';
import { useActionTimelineSync } from '@/hooks/useActionTimelineSync';
import type  { LyricLine, FeedbackMessage } from '@/types/game';
import { useGameStore } from '@/store/gameStore';
import { gameEndApi } from '@/api/game';
import  VoiceButton from '@/components/VoiceButton'
import './GamePage.css';

function GamePage() {
  const navigate = useNavigate();

  // === 상태 / 참조 ===
  const motionVideoRef = useRef<HTMLVideoElement | null>(null); // 동작 영상
  const videoRef = useRef<HTMLVideoElement | null>(null);       // 카메라 영상
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const captureTimeoutsRef = useRef<number[]>([]);
  const countdownTimerRef = useRef<number | null>(null);
  const hasNavigatedRef = useRef(false);
  const songBpmRef = useRef<number>(120);
  const currentSectionRef = useRef<'intro' | 'break' | 'verse1' | 'verse2'>('break');
  const announcedSectionRef = useRef<SectionKey | null>(null);

  const [isCounting, setIsCounting] = useState(false);
  const [count, setCount] = useState(5);
  const [isGameStarted, setIsGameStarted] = useState(false);
  const [lyrics, setLyrics] = useState<LyricLine[]>([]);
  const [sectionMessage, setSectionMessage] = useState<string | null>(null);
  const [wsMessage, setWsMessage] = useState<string | null>(null);
  const [redirectReason, setRedirectReason] = useState<null | 'wsError' | 'timeout'>(null);
  const [lastFeedback, setLastFeedback] = useState<FeedbackMessage['data'] | null>(null);
  const feedbackHideTimerRef = useRef<number | null>(null);

  const { connect, disconnect, sendFrame, isConnected } = useGameWs({
    onError: () => {
      setWsMessage('웹소켓 연결 실패');   // 문구 먼저 노출
      setRedirectReason('wsError');       // 이동은 별도 effect에서 지연 처리
    },
    onDisconnect: () => {
      // 최초 연결 이후 끊김: 배너만 띄우고 기다리면 stomp가 자동 재연결
      setWsMessage('연결이 끊어졌습니다. 재시도 중…');
    },
    onFeedback: (msg) => {
      // 기존 타이머 제거
      if (feedbackHideTimerRef.current) {
        clearTimeout(feedbackHideTimerRef.current);
        feedbackHideTimerRef.current = null;
      }
      console.log('[피드백] ', msg.data.judgment);

      // 새 피드백 저장
      setLastFeedback(msg.data);

      // 1초 정도 보여주고 자동으로 숨김
      feedbackHideTimerRef.current = window.setTimeout(() => {
        setLastFeedback(null);
        feedbackHideTimerRef.current = null;
      }, 1000);
    },
  });

  const { isCapturing, start: startStream, stop: stopStream } = useFrameStreamer({
    videoRef, audioRef, canvasRef,
  });
  const { stream, isReady, error, startCamera, stopCamera } = useCamera();

  const {
    sessionId,
    songTitle,
    songArtist,
    audioUrl,
    //videoUrls, // 필요 시 사용
    bpm,
    duration,
    sectionInfo,
    segmentInfo,
    lyricsInfo,
    verse1Timeline,
    verse2Timelines,
  } = useGameStore();

  const { current: currentLyric, next: nextLyric, isInstrumental } =
    useLyricsSync(audioRef, lyrics, { prerollSec: 0.04 });

  const currentActionName = useActionTimelineSync({
    audioRef,
    currentSectionRef,
    verse1Timeline,
    verse2Timelines,
    sectionInfo,
    verse2Level: 'level2',  // 또는 상태 기반으로 동적으로 설정 가능
  });

  // === 영상 메타 ===
  // 필요 시 videoUrls를 활용해 교체 가능합니다.
  const pub = (p: string) => `${import.meta.env.BASE_URL}${p}`;
  const VIDEO_META = {
    intro:  { src: pub('break.mp4'),      bpm: 100,  loopBeats: 8  },
    break:  { src: pub('break.mp4'),      bpm: 100,  loopBeats: 8  },
    verse1: { src: pub('part1.mp4'),      bpm: 98.6, loopBeats: 16 },
    verse2: { src: pub('part2_level2.mp4'), bpm: 99, loopBeats: 16 },
  } as const;
  type SectionKey = keyof typeof VIDEO_META;

  // === 수동 루프 파라미터 ===
  const LOOP_EPS = 0.02;     // 경계 여유
  const LOOP_RESTART = 0.04; // 되감을 위치(싱크 보정)

  const getLoopLenSec = (section: SectionKey) => {
    const { bpm, loopBeats } = VIDEO_META[section];
    return (60 / bpm) * loopBeats;
  };

  // === 모니터링 (섹션 감지 → 영상 전환) ===
  const { loadFromGameStart, startMonitoring, stopMonitoring } = useMusicMonitor({
    audioRef,
    onSectionEnter: (label) => {
      const map = { intro: 'intro', break: 'break', verse1: 'verse1', verse2: 'verse2' } as const;
      const nextSection = map[label] ?? 'break';
      switchSectionVideo(nextSection);

      if (nextSection !== announcedSectionRef.current) {
        announcedSectionRef.current = nextSection;
        if (nextSection === 'intro') {
          setSectionMessage("노래에 맞춰 캐릭터의 동작을 따라해주세요!");
          setTimeout(() => setSectionMessage(null), 8000);
        }
        if (nextSection === 'break') {
          setSectionMessage('잘 따라하셔서 2절은 한 단계 높은 동작으로 바꿔볼게요!');
          window.setTimeout(() => setSectionMessage(null), 12000);
        }
      }
    },
  });

  // 웹소켓 연결 확인
  useEffect(() => {
    if (isConnected || redirectReason) {
      if (isConnected) setWsMessage(null);
      return;
    }
    setWsMessage('웹소켓 연결 중…');
    const timer = window.setTimeout(() => {
      setWsMessage('연결이 지연되어 튜토리얼로 이동합니다.');
      setRedirectReason('timeout');
    }, 5000);
    return () => clearTimeout(timer);
  }, [isConnected, redirectReason]);

  // 안내 문구를 화면에 보여준 다음 1.2초 뒤 라우팅
  useEffect(() => {
    if (!redirectReason) return;
    const timer = window.setTimeout(() => {
      navigate('/tutorial', { replace: true });
    }, 1200);
    return () => clearTimeout(timer);
  }, [redirectReason, navigate]);


  // 자동 카운트다운
  useEffect(() => {
    const readyToStart = !!(isReady && audioRef.current?.src && isConnected);
    if (readyToStart && !isGameStarted && !isCounting && !countdownTimerRef.current) {
      startCountdown();
    }
  }, [isReady, isGameStarted, isCounting, isConnected]);

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

  // === 섹션별 영상 전환 ===
  function switchSectionVideo(next: SectionKey) {
    const mv = motionVideoRef.current;
    const au = audioRef.current;
    if (!mv) return;

    currentSectionRef.current = next;

    const { src, bpm: videoBpm } = VIDEO_META[next];
    const shouldPlayNow = !!au && !au.paused;
    const needSrcSwap = mv.src !== src;

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
      if (mv.readyState < 1) {
        mv.addEventListener('loadedmetadata', applyAndPlay, { once: true });
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
    if (!isConnected || !audioRef.current || !isReady) return;
    startMonitoring();

    // 오디오 먼저 재생
    await audioRef.current.play().catch(e => console.warn('audio play err', e));

    scheduleRangeCaptures(); // 구간 캡처/스트리밍 시작
  }

  // === 구간 캡처 스케줄링(서버 segmentInfo 사용) ===
  function scheduleRangeCaptures() {
    const audio = audioRef.current;
    if (!audio || !segmentInfo) return;

    clearCaptureTimeouts();

    const sid = sessionId!;

    const verse1 = segmentInfo.verse1cam;
    const verse2 = segmentInfo.verse2cam;
    const segments = [
      verse1 ? { key: 'verse1' as const, start: verse1.startTime, end: verse1.endTime } : null,
      verse2 ? { key: 'verse2' as const, start: verse2.startTime, end: verse2.endTime } : null,
    ].filter(Boolean) as Array<{ key: 'verse1' | 'verse2'; start: number; end: number }>;

    segments.forEach(({ start, end }) => {
      if (end <= start) return;

      const now = audio.currentTime;
      const delayMs = Math.max(0, (start - now) * 1000);

      const timeoutId = window.setTimeout(() => {
        const cur = audio.currentTime;
        if (cur >= end) return;

        const effectiveStart = Math.max(cur, start);
      startStream(effectiveStart, end, (blob, { t /*, idx*/ }) => {
        void sendFrame({ sessionId: sid, blob, currentPlayTime: t });
      });
      }, delayMs);

      captureTimeoutsRef.current.push(timeoutId);
    });
  }

  function clearCaptureTimeouts() {
    captureTimeoutsRef.current.forEach(id => clearTimeout(id));
    captureTimeoutsRef.current = [];
  }

  // === 카운트다운 ===
  function startCountdown() {
    if (isGameStarted || isCounting || !isConnected ) return;
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
    disconnect();
    if (audioRef.current) audioRef.current.pause();

    gameEndApi();
    navigate('/result');
  }

  function mapJudgment(judgment: 1 | 2 | 3) {
    switch (judgment) {
      case 3:
        return { label: 'PERFECT', labelKo: '퍼펙트!', level: 'perfect' as const };
      case 2:
        return { label: 'GOOD', labelKo: '좋아요!', level: 'good' as const };
      case 1:
      default:
        return { label: 'SOSO', labelKo: '조금 더!', level: 'soso' as const };
    }
  }

  function formatTime(sec: number) {
    const s = Math.floor(sec);
    const mm = String(Math.floor(s / 60)).padStart(2, '0');
    const ss = String(s % 60).padStart(2, '0');
    return `${mm}:${ss}`;
  }


  // === 초기화: store 기반으로만 세팅 ===
  useEffect(() => {
    // let cancelled = false;
    (async () => {
      try {
        startCamera();

        // 필수 데이터 가드
        if (!audioUrl || !bpm || !duration || !sectionInfo || !sessionId) {
          console.warn('필수 게임 데이터가 없습니다. 튜토리얼로 이동합니다.');
          navigate('/tutorial', { replace: true });
          return;
        }

        // 오디오 소스
        if (audioRef.current) {
          const localAudio = pub(audioUrl);
          audioRef.current.src = localAudio;
          audioRef.current.onerror = () => {
            if (audioUrl) {
              audioRef.current!.src = audioUrl;
              audioRef.current!.load();
            }
          };
          audioRef.current.load();
        }

        // 가사/메타
        setLyrics(lyricsInfo.lines ?? []);
        songBpmRef.current = bpm;

        // useMusicMonitor가 기대하는 timeline 형태로 매핑
        const timeline = {
          introStartTime: sectionInfo.introStartTime ?? 0,
          verse1StartTime: sectionInfo.verse1StartTime ?? 0,
          breakStartTime: sectionInfo.breakStartTime ?? 0,
          verse2StartTime: sectionInfo.verse2StartTime ?? 0,
        };

        connect(sessionId);

        await loadFromGameStart({ bpm, duration, timeline });
        switchSectionVideo('break');
      } catch (e) {
        console.error('게임 시작 초기화 실패:', e);
      }
    })();

    return () => {
      // cancelled = true;
      stopCamera();
      stopMonitoring();
      stopStream();
      clearCaptureTimeouts();
      if (audioRef.current) audioRef.current.pause();
    };
  }, []);

  return (
    <>
      {isCounting && (
        <div className="countdown-overlay">
          <div className="countdown-bubble">{count > 0 ? count : 'Go!'}</div>
        </div>
      )}
      {sectionMessage && (
        <div className="section-message-overlay">
          <div className="section-message-bubble">
            {sectionMessage}
          </div>
        </div>
      )}
      {wsMessage && (
        <div className="ws-message-overlay">
          <div className="ws-message-bubble">{wsMessage}</div>
        </div>
      )}

      <div className="game-page">
        <div className="left-container">
          <div className="left__top">
            <audio controls ref={audioRef} style={{ display: 'block', width: '40%', height: '20%' }} />
          </div>
          <div className="left__main">
            <div className="character-section">
              <video
                ref={motionVideoRef}
                preload="auto"
                muted
                playsInline
                src={VIDEO_META.break.src}
                className="motion-video"
                style={{ width: '800px' }}
              />
              {currentActionName && (
                <div className="action-label-overlay">
                  {currentActionName}
                </div>
              )}
            </div>
            <div className="lyrics-container">
              <div className="lyrics-display">
                <div className="lyrics-current">{isInstrumental ? '(간주 중)' : currentLyric?.text ?? '\u00A0'}</div>
                <div className="lyrics-next">{!isInstrumental ? nextLyric?.text ?? '\u00A0' : '\u00A0'}</div>
              </div>
            </div>
          </div>
        </div>

          <div className="right-container">
            <div className="right__top">
              <div className="song-title">{songTitle}</div>
              <div className="song-artist">{songArtist}</div>
            </div>
            <div className="right__main">
              <div className="camera-section">
                <video ref={videoRef} autoPlay playsInline muted className="camera-video" />
                <canvas ref={canvasRef} className="capture-canvas" />

                <div className="segment-info">
                  {isCapturing && <span className="capturing-badge">📹 캡처 중</span>}
                </div>

                {error && <div className="error-message">❌ {error}</div>}
                {!isReady && !error && <div className="loading-message">📹 카메라 준비 중...</div>}
              </div>
              <div className="feedback-section">
                {lastFeedback ? (
                  (() => {
                    const { judgment, timestamp } = lastFeedback;
                    const mapped = mapJudgment(judgment);
                    return (
                      <div className={`feedback-badge feedback-${mapped.level}`}>
                        <div className="feedback-main-text">{mapped.labelKo}</div>
                        <div className="feedback-sub-text">
                          {mapped.label} · {formatTime(timestamp)}
                        </div>
                      </div>
                    );
                  })()
                ) : (
                  <span className="feedback-placeholder"></span>
                )}
              </div>
            </div>
          </div>
        <VoiceButton />
      </div>
    </>
  );
}

export default GamePage;
