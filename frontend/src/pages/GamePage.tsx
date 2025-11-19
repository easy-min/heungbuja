import { useRef, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCamera } from '@/hooks/useCamera';
import { useFrameStreamer } from '@/hooks/useFrameStreamer';
import { useMusicMonitor } from '@/hooks/useMusicMonitor';
// import { useLyricsSync } from '@/hooks/useLyricsSync';
import { useGameWs } from '@/hooks/useGameWs';
import { useActionTimelineSync } from '@/hooks/useActionTimelineSync';
import type  { FeedbackMessage, GameEndResponse, GameWsMessage } from '@/types/game';
import { useGameStore } from '@/store/gameStore';
import { gameEndApi } from '@/api/game';
import  VoiceButton from '@/components/VoiceButton'
import LoadingDots from '@/components/icons/LoadingDots';
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
  const verse2LevelRef = useRef<'level1' | 'level2' | 'level3'>('level2');
  const forceStopRef = useRef(false);
  const hasLevelDecisionRef = useRef(false);
  const sectionMessageTimerRef = useRef<number | null>(null);

  // === 패턴 프리로드용 ===
  const preloadedPatternVideosRef =
    useRef<Partial<Record<PatternKey, HTMLVideoElement>>>({});
  const patternReadyRef = useRef<Record<PatternKey, boolean>>({
    P1: false,
    P2: false,
    P3: false,
    P4: false,
  });

  // === 영상 전환 디버깅용
  const driftSamplesRef = useRef<number[]>([]);
  const lastLoopBoundaryRef = useRef<number | null>(null);
  const switchLatenciesRef = useRef<number[]>([]);
  const patternSwitchLatenciesRef = useRef<number[]>([]);
  const frameIntervalsRef = useRef<number[]>([]);
  const captureCostRef = useRef<number[]>([]);

  const pendingSwitchRef = useRef<{
    section: SectionKey;
    requestedAt: number;
  } | null>(null);

  const pendingPatternSwitchRef = useRef<{
    pattern: PatternKey;
    requestedAt: number;
  } | null>(null);

  const currentPatternSeqRef = useRef<PatternKey[] | null>(null);
  const currentPatternIndexRef = useRef<number>(0);
  const currentPatternKeyRef = useRef<PatternKey | null>(null);

  const [isCounting, setIsCounting] = useState(false);
  const [count, setCount] = useState(5);
  const [isGameStarted, setIsGameStarted] = useState(false);
  // const [lyrics, setLyrics] = useState<LyricLine[]>([]);
  const [sectionMessage, setSectionMessage] = useState<string | null>(null);
  const [wsMessage, setWsMessage] = useState<string | null>(null);
  const [redirectReason, setRedirectReason] = useState<null | 'wsError' | 'timeout'>(null);
  const [lastFeedback, setLastFeedback] = useState<FeedbackMessage['data'] | null>(null);
  const feedbackHideTimerRef = useRef<number | null>(null);
  const [verse2Level, setVerse2Level] = useState<'level1' | 'level2' | 'level3'>('level2');

  const { connect, disconnect, sendFrame, isConnected } = useGameWs({
    onError: () => {
      if (forceStopRef.current) return;
      setWsMessage('웹소켓 연결 실패');   // 문구 먼저 노출
      setRedirectReason('wsError');       // 이동은 별도 effect에서 지연 처리
    },
    onDisconnect: () => {
      if (forceStopRef.current || hasNavigatedRef.current) return;
      // 최초 연결 이후 끊김: 배너만 띄우고 기다리면 stomp가 자동 재연결
      setWsMessage('연결이 끊어졌습니다. 재시도 중…');
    },
    onFeedback: (msg: GameWsMessage) => {
      if (msg.type === 'LEVEL_DECISION') {
        const { nextLevel } = msg.data;
        console.log(msg.data);

        const levelKey = (`level${nextLevel}` as 'level1' | 'level2' | 'level3');
        verse2LevelRef.current = levelKey;
        setVerse2Level(levelKey);
        hasLevelDecisionRef.current = true;

        if (currentSectionRef.current === 'break') {
          const msgText = getBreakMessageByLevel(levelKey);
          showSectionMessage(msgText, 12000);
        }

        if (currentSectionRef.current === 'verse2') {
          // 🔹 난이도 변경에 따른 verse2 재전환도 측정
          pendingSwitchRef.current = {
            section: 'verse2',
            requestedAt: performance.now(),
          };
          switchSectionVideo('verse2', levelKey);
        }
        return;
      }

      if (feedbackHideTimerRef.current) {
        clearTimeout(feedbackHideTimerRef.current);
        feedbackHideTimerRef.current = null;
      }

      setLastFeedback(msg.data);

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
    // lyricsInfo,
    verse1Timeline,
    verse2Timelines,
    stopRequested,
    clear,
    sectionPatterns,
  } = useGameStore();

  // const { current: currentLyric, next: nextLyric, isInstrumental } =
  //   useLyricsSync(audioRef, lyrics, { prerollSec: 0.04 });

  const currentActionName = useActionTimelineSync({
    audioRef,
    currentSectionRef,
    verse1Timeline,
    verse2Timelines,
    sectionInfo,
    verse2Level,
  });

  // === 영상 메타 ===
  const pub = (p: string) => `${import.meta.env.BASE_URL}${p}`;

  const VIDEO_META = {
    intro:  { src: pub('break.mp4'),      bpm: 100,  loopBeats: 8  },
    break:  { src: pub('break.mp4'),      bpm: 100,  loopBeats: 8  },
  } as const;

  const PATTERN_META: Record<PatternKey, { src: string; bpm: number; loopBeats: number }> = {
    P1: { src: pub('P1.mp4'), bpm: 98.6, loopBeats: 16 },
    P2: { src: pub('P2.mp4'), bpm: 98.3, loopBeats: 16 },
    P3: { src: pub('P3.mp4'), bpm: 99, loopBeats: 16 },
    P4: { src: pub('P4.mp4'), bpm: 99, loopBeats: 16 },
  };

  type SectionKey = 'intro' | 'break' | 'verse1' | 'verse2';
  type PatternKey = 'P1' | 'P2' | 'P3' | 'P4';

  // === 수동 루프 파라미터 ===
  const LOOP_EPS = 0.02;     // 경계 여유
  const LOOP_RESTART = 0.06; // 되감을 위치(싱크 보정)

  function getPatternSequenceForSection(
    section: SectionKey,
    levelForVerse2: 'level1' | 'level2' | 'level3',
  ): PatternKey[] | null {
    if (!sectionPatterns) return null;

    if (section === 'verse1') {
      return (sectionPatterns.verse1 ?? []) as PatternKey[];
    }

    if (section === 'verse2') {
      const arr = sectionPatterns.verse2?.[levelForVerse2] ?? [];
      return arr as PatternKey[];
    }

    // intro, break 는 패턴 없이 단일 영상
    return null;
  }

  function showSectionMessage(message: string, durationMs: number) {
    if (sectionMessageTimerRef.current) {
      clearTimeout(sectionMessageTimerRef.current);
      sectionMessageTimerRef.current = null;
    }

    setSectionMessage(message);
    sectionMessageTimerRef.current = window.setTimeout(() => {
      setSectionMessage(null);
      sectionMessageTimerRef.current = null;
    }, durationMs);
  }

  function getBreakMessageByLevel(
    level: 'level1' | 'level2' | 'level3'
  ): string {
    switch (level) {
      case 'level1':
        return '잘 하고 계세요! 조금만 더 힘내세요!';
      case 'level2':
        return '잘 따라하셔서 2절은 한 단계 높은 동작으로 바꿔볼게요!';
      case 'level3':
        return '멋진 실력이에요! 2절은 최상 난이도로 함께해요!';
    }
  }

  const getLoopLenSec = (section: SectionKey): number => {
    // verse1 / verse2는 현재 패턴 기준으로 길이 계산
    if (section === 'verse1' || section === 'verse2') {
      const key = currentPatternKeyRef.current;
      if (key) {
        const meta = PATTERN_META[key];
        return (60 / meta.bpm) * meta.loopBeats;
      }
      // 패턴이 아직 선택 안 된 경우, 안전한 기본값 반환
      return 1;
    }

    // 여기까지 내려오면 section 은 intro | break
    const simpleSection: 'intro' | 'break' =
      section === 'intro' ? 'intro' : 'break';

    const meta = VIDEO_META[simpleSection];
    return (60 / meta.bpm) * meta.loopBeats;
  };

  // === 모니터링 (섹션 감지 → 영상 전환) ===
  const { loadFromGameStart, startMonitoring, stopMonitoring } = useMusicMonitor({
    audioRef,
    onSectionEnter: (label) => {
      const map = { intro: 'intro', break: 'break', verse1: 'verse1', verse2: 'verse2' } as const;
      const nextSection = map[label] ?? 'break';
      // 🔹 섹션 전환 요청 시각 기록
      pendingSwitchRef.current = {
        section: nextSection,
        requestedAt: performance.now(),
      };
      switchSectionVideo(nextSection);

      if (nextSection !== announcedSectionRef.current) {
        announcedSectionRef.current = nextSection;

        if (nextSection === 'intro') {
          showSectionMessage('노래에 맞춰 캐릭터의 동작을 따라해주세요!', 8000);
        }

        if (nextSection === 'break') {
          // 🔹 난이도 결정이 아직 안 났으면, 여기서는 문구를 띄우지 않음
          if (!hasLevelDecisionRef.current) {
            return;
          }

          const msg = getBreakMessageByLevel(verse2LevelRef.current);
          showSectionMessage(msg, 12000);
        }
      }
    },
  });

  // 루프 경계 지점에 도달했을 때 기록 (tick 안에서)
  function markLoopBoundary() {
    lastLoopBoundaryRef.current = performance.now();
  }

  // === 패턴 비디오 프리로드 ===
  useEffect(() => {
    const videos: Partial<Record<PatternKey, HTMLVideoElement>> = {};

    (Object.keys(PATTERN_META) as PatternKey[]).forEach((key) => {
      const meta = PATTERN_META[key];
      const v = document.createElement('video');

      v.src = meta.src;
      v.preload = 'auto';
      v.muted = true;

      const handleCanPlay = () => {
        patternReadyRef.current[key] = true;
        // 필요하면 로그 확인
        console.log(`[preload] pattern ${key} canplaythrough`);
      };

      const handleError = (ev: Event) => {
        console.error(`[preload] pattern ${key} error`, ev);
      };

      v.addEventListener('canplaythrough', handleCanPlay);
      v.addEventListener('error', handleError);

      v.load(); // 실제 로딩 시작
      videos[key] = v;
    });

    preloadedPatternVideosRef.current = videos;

    // 정리
    return () => {
      Object.values(videos).forEach((v) => {
        if (!v) return;
        v.pause();
        v.removeAttribute('src');
        v.load();
      });
    };
  }, []);

  useEffect(() => {
    let last = performance.now();
    let raf = 0;
    const tick = () => {
      const now = performance.now();
      frameIntervalsRef.current.push(now - last);
      last = now;
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, []);

  // 비디오가 실제 재생을 시작할 때 (playing 이벤트 시점)
  useEffect(() => {
    const mv = motionVideoRef.current;
    if (!mv) return;

    const onPlaying = () => {
      const now = performance.now();

      // 🔹 섹션 전환 latency
      const pendingSection = pendingSwitchRef.current;
      if (pendingSection) {
        const latency = now - pendingSection.requestedAt;
        if (pendingSection.section === 'verse1' || pendingSection.section === 'verse2') {
          switchLatenciesRef.current.push(latency);
        }
        pendingSwitchRef.current = null;
      }

      // 🔹 패턴 전환 latency
      const pendingPattern = pendingPatternSwitchRef.current;
      if (pendingPattern) {
        const latency = now - pendingPattern.requestedAt;
        patternSwitchLatenciesRef.current.push(latency);
        pendingPatternSwitchRef.current = null;
      }
    };

    mv.addEventListener('playing', onPlaying);
    return () => mv.removeEventListener('playing', onPlaying);
  }, []);

  useEffect(() => {
    let rafId = 0;
    const sample = () => {
      const audio = audioRef.current;
      const mv = motionVideoRef.current;
      if (audio && mv && !audio.paused) {
        const driftSec = mv.currentTime - audio.currentTime;
        const driftMs = driftSec * 1000;
        driftSamplesRef.current.push(driftMs);
      }
      rafId = requestAnimationFrame(sample);
    };
    rafId = requestAnimationFrame(sample);
    return () => cancelAnimationFrame(rafId);
  }, []);

  // function printSectionSwitchLatencyStats() {
  //   const arr = switchLatenciesRef.current;
  //   if (!arr.length) return;
  //   const sum = arr.reduce((a, b) => a + b, 0);
  //   const mean = sum / arr.length;
  //   const max = Math.max(...arr);
  //   const min = Math.min(...arr);
  //   console.table({ count: arr.length, mean, max, min });
  // }

  // function printPatternSwitchLatencyStats() {
  //   const arr = patternSwitchLatenciesRef.current;
  //   if (!arr.length) return;
  //   const sum = arr.reduce((a, b) => a + b, 0);
  //   const mean = sum / arr.length;
  //   const max = Math.max(...arr);
  //   const min = Math.min(...arr);
  //   console.table({ count: arr.length, mean, max, min });
  // }

  // function printFrameStats() {
  //   const arr = frameIntervalsRef.current;
  //   if (!arr.length) return;
  //   const sum = arr.reduce((a, b) => a + b, 0);
  //   const mean = sum / arr.length;
  //   const max = Math.max(...arr);
  //   console.table({ count: arr.length, mean, max });
  // }

  // function printCaptureCostStats() {
  //   const arr = captureCostRef.current;
  //   if (!arr.length) return;
  //   const sum = arr.reduce((a, b) => a + b, 0);
  //   const mean = sum / arr.length;
  //   const max = Math.max(...arr);
  //   const min = Math.min(...arr);
  //   console.table({ count: arr.length, mean, max, min });
  // }

  // // 디버그 헬퍼들을 window에 노출 (개발 모드에서만)
  // useEffect(() => {
  //   if (import.meta.env.PROD) return;

  //   (window as any).printSectionSwitchLatencyStats = printSectionSwitchLatencyStats;
  //   (window as any).printPatternSwitchLatencyStats = printPatternSwitchLatencyStats;
  //   (window as any).printFrameStats = printFrameStats;
  //   (window as any).printCaptureCostStats = printCaptureCostStats;
  // }, []);

  // 웹소켓 연결 확인
  useEffect(() => {
    if (forceStopRef.current) return;
    if (hasNavigatedRef.current) return;
    if (stopRequested) return;
    if (isConnected || redirectReason) {
      if (isConnected) setWsMessage(null);
      return;
    }
    setWsMessage('웹소켓 연결 중…');
    const timer = window.setTimeout(() => {
      if (forceStopRef.current) return;
      if (stopRequested) return;
      setWsMessage('연결이 지연되어 튜토리얼로 이동합니다.');
      setRedirectReason('timeout');
    }, 5000);
    return () => clearTimeout(timer);
  }, [isConnected, redirectReason, stopRequested]);

  // 안내 문구를 화면에 보여준 다음 1.2초 뒤 라우팅
  useEffect(() => {
    if (forceStopRef.current) return;
    if (!redirectReason || stopRequested) return;
    const timer = window.setTimeout(() => {
      navigate('/tutorial', { replace: true });
    }, 1200);
    return () => clearTimeout(timer);
  }, [redirectReason, navigate, stopRequested]);


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

  useEffect(() => {
    if (!stopRequested) return;
    void handleForceStop();
  }, [stopRequested]);

  // === 섹션별 영상 전환 ===
  function switchSectionVideo(
    next: SectionKey,
    overrideLevel?: 'level1' | 'level2' | 'level3',
  ) {
    const mv = motionVideoRef.current;
    const au = audioRef.current;
    if (!mv) return;

    currentSectionRef.current = next;

    // verse2 난이도 결정
    const levelToUse =
      next === 'verse2'
        ? (overrideLevel ?? verse2LevelRef.current)
        : verse2LevelRef.current;

    // intro / break → 기존 방식
    if (next === 'intro' || next === 'break') {
      currentPatternSeqRef.current = null;
      currentPatternIndexRef.current = 0;
      currentPatternKeyRef.current = null;

      const { src, bpm: videoBpm } = VIDEO_META[next];
      const songBpm = songBpmRef.current || 120;
      const shouldPlayNow = !!au && !au.paused;
      const needSrcSwap = mv.src !== src;

      const applyAndPlay = async () => {
        mv.loop = false;
        mv.pause();
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
      return;
    }

    // verse1 / verse2 → 패턴 배열 기반
    const seq = getPatternSequenceForSection(next, levelToUse);
    currentPatternSeqRef.current = seq;
    currentPatternIndexRef.current = 0;

    const firstPattern = seq && seq.length > 0 ? seq[0] : null;
    const shouldPlayNow = !!au && !au.paused;

    if (firstPattern) {
      pendingPatternSwitchRef.current = {
        pattern: firstPattern,
        requestedAt: performance.now(),
      };
      void playPatternVideo(firstPattern, shouldPlayNow);
    } else {
      // 패턴이 비어 있는 경우 안전하게 아무것도 하지 않음
      console.warn('섹션 패턴이 비어 있습니다:', next, levelToUse);
    }
  }

  async function playPatternVideo(
    pattern: PatternKey,
    videoShouldFollowAudio: boolean,
  ) {
    const mv = motionVideoRef.current;
    const au = audioRef.current;
    if (!mv) return;

    // 프리로드 상태 확인 (디버그용)
    if (!patternReadyRef.current[pattern]) {
      console.warn(
        `[pattern] ${pattern} is not fully preloaded yet (ready=false).`,
      );
    }

    const { src, bpm: videoBpm } = PATTERN_META[pattern];
    const songBpm = songBpmRef.current || 120;

    currentPatternKeyRef.current = pattern;

    const needSrcSwap = mv.src !== src;

    const applyAndPlay = async () => {
      mv.loop = false;
      mv.pause();
      mv.playbackRate = songBpm / videoBpm;
      mv.currentTime = LOOP_RESTART;
      if (videoShouldFollowAudio && au && !au.paused) {
        await mv.play().catch(() => {});
      }
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

    const advancePatternIfNeeded = () => {
      const seq = currentPatternSeqRef.current;
      if (!seq || seq.length === 0) {
        // 패턴 모드가 아니면 그냥 현재 영상만 루프
        mv.currentTime = LOOP_RESTART;
        if (mv.paused) { mv.play().catch(() => {}); }
        return;
      }

      // 다음 패턴 index 계산 (마지막이면 0으로)
      const nextIndex = (currentPatternIndexRef.current + 1) % seq.length;
      currentPatternIndexRef.current = nextIndex;
      const nextPattern = seq[nextIndex];

      const au = audioRef.current;
      const shouldPlayNow = !!au && !au.paused;

      pendingPatternSwitchRef.current = {
        pattern: nextPattern,
        requestedAt: performance.now(),
      };

      void playPatternVideo(nextPattern, shouldPlayNow);
    };

    const tick = () => {
      raf = requestAnimationFrame(tick);
      if (mv.readyState < 2) return;

      const section = currentSectionRef.current;
      const nominal = getLoopLenSec(section);
      const dur = Number.isFinite(mv.duration) ? mv.duration : nominal;
      const loopEnd = Math.min(nominal, dur);

      if (mv.currentTime >= loopEnd - LOOP_EPS) {
        markLoopBoundary();
        if (section === 'verse1' || section === 'verse2') {
          advancePatternIfNeeded();
        } else {
          mv.currentTime = LOOP_RESTART;
          if (mv.paused) { mv.play().catch(() => {}); }
        }
      }
    };

    const onEnded = () => {
      markLoopBoundary();
      // 비정상적으로 ended 이벤트가 와도 현재 섹션/패턴에 맞게 처리
      const section = currentSectionRef.current;
      if (section === 'verse1' || section === 'verse2') {
        advancePatternIfNeeded();
      } else {
        mv.currentTime = LOOP_RESTART;
        mv.play().catch(() => {});
      }
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
        const start = performance.now();
        void sendFrame({ sessionId: sid, blob, currentPlayTime: t }).finally(() => {
          const end = performance.now();
          captureCostRef.current.push(end - start);
        });
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
  async function goToResultOnce() {
    if (hasNavigatedRef.current) return;
    hasNavigatedRef.current = true;

    forceStopRef.current = true;
    setWsMessage(null);
    setRedirectReason(null);

    stopMonitoring();
    stopCamera();
    stopStream();
    clearCaptureTimeouts();
    disconnect();
    if (audioRef.current) {
      audioRef.current.onerror = null;
      audioRef.current.pause();
    }

    const res: GameEndResponse = await gameEndApi();

    navigate('/result', {
      state: {
        finalScore: res.finalScore,
        message: res.message,
      },
    });
  }

  function cleanupGameResources() {
    stopMonitoring();
    stopCamera();
    stopStream();
    clearCaptureTimeouts();
    disconnect();
    if (audioRef.current) {
      audioRef.current.onerror = null;
      audioRef.current.pause();
    }
    const mv = motionVideoRef.current;
    if (mv) {
      mv.pause();
      mv.currentTime = 0;   // 필요하면 처음 프레임으로
    }
  }

  async function handleForceStop() {
    forceStopRef.current = true;
    cleanupGameResources();
    setRedirectReason(null);
    setWsMessage(null);
    clear();
  }

  function mapJudgment(judgment: 1 | 2 | 3) {
    switch (judgment) {
      case 3:
        return { label: 'PERFECT', labelKo: '완벽해요!', level: 'perfect' as const };
      case 2:
        return { label: 'GOOD', labelKo: '좋아요!', level: 'good' as const };
      case 1:
      default:
        return { label: 'SOSO', labelKo: '조금 더!', level: 'soso' as const };
    }
  }

  // function formatTime(sec: number) {
  //   const s = Math.floor(sec);
  //   const mm = String(Math.floor(s / 60)).padStart(2, '0');
  //   const ss = String(s % 60).padStart(2, '0');
  //   return `${mm}:${ss}`;
  // }


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
            const el = audioRef.current;
            if (!el || !audioUrl) return;

            el.src = audioUrl;
            el.load();
          };
          audioRef.current.load();
        }

        // 가사/메타
        // setLyrics(lyricsInfo.lines ?? []);
        songBpmRef.current = bpm;

        // useMusicMonitor가 기대하는 timeline 형태로 매핑
        const timeline = {
          introStartTime: sectionInfo.introStartTime ?? 0,
          verse1StartTime: sectionInfo.verse1StartTime ?? 0,
          breakStartTime: sectionInfo.breakStartTime ?? 0,
          verse2StartTime: sectionInfo.verse2StartTime ?? 0,
        };

        connect(sessionId);
        forceStopRef.current = false;

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
      if (audioRef.current) {
        audioRef.current.onerror = null;
        audioRef.current.pause();
      };
      if (sectionMessageTimerRef.current) {
        clearTimeout(sectionMessageTimerRef.current);
      };
    };
  }, []);

  return (
    <>
      {/* 카운트 시작 전: 로딩 점만 전체 화면에 표시 */}
      {!isCounting && !isGameStarted && (
        <div className="game-loading-overlay">
          <LoadingDots className="game-loading-dots" />
        </div>
      )}

      {isCounting && (
        <div className="countdown-overlay">
          <div className="countdown-bubble">{count > 0 ? count : 'Go!'}</div>
        </div>
      )}

      {wsMessage && (isCounting || isGameStarted) && (
        <div className="ws-message-overlay">
          <div className="ws-message-bubble">{wsMessage}</div>
        </div>
      )}

      <div className="game-page">
        <div className='top-container'>
          <div className='song-info'>
            <div className="game-song-title">{songTitle}</div>
            <div className="game-song-artist">{songArtist}</div>
          </div>
          <div className="audio-bar">
            <audio
              controls
              ref={audioRef}
              className="audio-player"
            />
          </div>
        </div>
        {/* 메인 영역: 좌(캐릭터) / 우(카메라) */}
        <div className="game-main">

          {/* 왼쪽: 카메라 + 피드백 */}
          <div className="left-container">
            <div className="left__main">
              <div className="camera-section">
                <video
                  ref={videoRef}
                  autoPlay
                  playsInline
                  muted
                  className="camera-video"
                />
                <canvas ref={canvasRef} className="capture-canvas" />

                <div className="segment-info">
                  {isCapturing && <span className="capturing-badge">📹 동작 인식 중</span>}
                </div>

                {error && <div className="error-message">❌ {error}</div>}
                {!isReady && !error && (
                  <div className="loading-message">📹 카메라 준비 중...</div>
                )}
              </div>

              <div className="feedback-section">
                {lastFeedback ? (
                  (() => {
                    const { judgment } = lastFeedback;
                    const mapped = mapJudgment(judgment);
                    return (
                      <div className={`feedback-badge feedback-${mapped.level}`}>
                        <div className="feedback-main-text">{mapped.labelKo}</div>
                      </div>
                    );
                  })()
                ) : (
                  <span className="feedback-placeholder" />
                )}
              </div>
            </div>
          </div>

          {/* 오른쪽: 캐릭터 */}
          <div className="right__main">
            <div className="character-section">
              <div className="motion-video-wrapper">
                <video
                  ref={motionVideoRef}
                  preload="auto"
                  muted
                  playsInline
                  src={VIDEO_META.break.src}
                  className="motion-video"
                />
              </div>
              {currentActionName && (
                <div className="action-label-overlay">
                  {currentActionName}
                </div>
              )}
              {sectionMessage && (
                <div className="section-message-overlay">
                  <div className="section-message-bubble">
                    {sectionMessage}
                  </div>
                </div>
              )}
            </div>
          </div>

          <VoiceButton />
        </div>
      </div>
    </>
  );
}

export default GamePage;
