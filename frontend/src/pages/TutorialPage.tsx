import { useEffect, useState, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { gameStartApi } from '@/api/game';
import { useGameStore } from '@/store/gameStore';
import { useCamera } from '@/hooks/useCamera';
import type { GameStartResponse } from '@/types/game';
import LoadingDots from '@/components/icons/LoadingDots';
import './TutorialPage.css';

type Step = 1 | 2 | 3;

function TutorialPage() {
  const nav = useNavigate();
  const location = useLocation();
  const setFromApi = useGameStore((s) => s.setFromApi);

  const [loading, setLoading] = useState(true);
  const [songId, setSongId] = useState<number | null>(null);
  const [step, setStep] = useState<Step>(1);
  const [isFinalMessage, setIsFinalMessage] = useState(false);
  const [showCheck, setShowCheck] = useState(false);
  
  // 카메라 훅 (웹소켓 없이 미리보기만 사용)
  const { stream, isReady, error, startCamera, stopCamera } = useCamera();
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const isCameraReady = !!stream && isReady && !error;

  // 타이머 관리 (단계 전환용)
  const timersRef = useRef<number[]>([]);
  const sequenceStartedRef = useRef(false);

  const isStep1 = step === 1;
  const cameraClass =
  step === 1
    ? 'camera-state-hidden'
    : step === 2
    ? 'camera-state-show-step2'
    : 'camera-state-show-step3';


  // 페이지 진입 시 게임 데이터 로드 (음성 명령 처리 포함)
  useEffect(() => {
    setLoading(true);

    const voiceCommandData = location.state as GameStartResponse | undefined;

    if (voiceCommandData?.gameInfo) {
      console.log('음성 명령으로 받은 게임 데이터를 store에 저장:', voiceCommandData);
      setFromApi(voiceCommandData);
      setSongId(voiceCommandData.gameInfo.songId);
      setLoading(false);
    } else {
      const initGameData = async () => {
        try {
          const res = await gameStartApi();
          setFromApi(res);
          setSongId(res.gameInfo.songId);
          console.log('게임 데이터 로드 완료:', res);
        } catch (e) {
          console.error('게임 데이터 로드 실패:', e);
        } finally {
          setLoading(false);
        }
      };
      initGameData();
    }
  }, [location.state, setFromApi]);

  // ===== 카메라 시작 / 정리 =====
  useEffect(() => {
    startCamera();

    return () => {
      stopCamera();
      timersRef.current.forEach((id) => clearTimeout(id));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 스트림을 비디오 엘리먼트에 연결
  useEffect(() => {
    if (stream && videoRef.current) {
      videoRef.current.srcObject = stream;
      // 필요하면 방어적으로 play까지 호출
      // void videoRef.current.play().catch(() => {});
    }
  }, [stream, step]);

  // ===== 단계 자동 진행 (카메라 준비 시간 제외) =====
  useEffect(() => {
    if (loading || !songId || !isCameraReady) return;
    if (sequenceStartedRef.current) return;

    sequenceStartedRef.current = true;
    setStep(1);
    setIsFinalMessage(false);

     // step1: 0s ~ 3s -> 2단계 진입
    const t1 = window.setTimeout(() => setStep(2), 3000);

    // 2 -> 3 단계: 6s ~ 8s 동안 체크(2초), 이후 3단계 진입
    const t2a = window.setTimeout(() => setShowCheck(true), 6000);
    const t2b = window.setTimeout(() => {
      setShowCheck(false);
      setStep(3);
    }, 8000);

    // 3 -> 최종 멘트: 11s ~ 13s 동안 체크(2초), 이후 최종 멘트
    const t3a = window.setTimeout(() => setShowCheck(true), 11000);
    const t3b = window.setTimeout(() => {
      setShowCheck(false);
      setIsFinalMessage(true);
    }, 13000);

    // 마지막 멘트 2초 정도 보여주고 게임 시작
    const t4 = window.setTimeout(() => {
      nav(`/game/${songId}`);
    }, 15000);

    timersRef.current = [t1, t2a, t2b, t3a, t3b, t4];

    return () => {
      timersRef.current.forEach((id) => clearTimeout(id));
    };
  }, [loading, songId, isCameraReady, nav]);

  // 단계별 문구
  const renderTitle = () => {
    if (loading || !isCameraReady) {
      return (
        <>
        {/* 카메라 준비 중 */}
          <LoadingDots className="tutorial-camera-loading"/>
        </>
      );
    }

    if (isFinalMessage) {
      return (
        <>
          좋아요!
          <br />
          이제 체조를 시작합니다!
        </>
      );
    }

    switch (step) {
      case 1:
        return (
          <>
            게임을 시작하기 전,
            <br />
            간단한 준비가 필요해요!
          </>
        );
      case 2:
        return (
          <>
            카메라에 상반신이
            <br />
            잘 나오도록 앉아주세요!
          </>
        );
      case 3:
        return (
          <>
            준비가 되면 머리 위로
            <br />
            동그라미를 만들어주세요!
          </>
        );
    }
  };

  return (
    <div className="tutorial-page">
      {/* 상단 단계 인디케이터 */}
      <div className="tutorial-steps">
        {[1, 2, 3].map((n) => {
          const isActive = step === n || (n === 3 && isFinalMessage);
          return (
            <div
              key={n}
              className={`tutorial-step-circle ${
                isActive
                  ? 'tutorial-step-circle--active'
                  : 'tutorial-step-circle--inactive'
              }`}
            >
              {n}
            </div>
          );
        })}
      </div>

      {/* 체크 애니메이션 오버레이 */}
      {showCheck && (
        <div className="step-check">
          <div className="step-check__outer">
            <div className="step-check__inner">
              <span className="step-check__mark">✓</span>
            </div>
          </div>
        </div>
      )}

      {/* 고정 레이아웃: 왼쪽 카메라 슬롯 + 오른쪽 텍스트 */}
      <div
        className={`tutorial-layout ${
          isStep1 ? 'tutorial-layout--step1' : 'tutorial-layout--step23'
        }`}
      >
        {/* ⬇⬇ 1단계가 아닐 때만 카메라 영역 렌더링 */}
        {!isStep1 && (
          <div className={`tutorial-camera-wrapper ${cameraClass}`}>
            <div className="tutorial-camera-outer">
              <div className="tutorial-camera-frame">
                {!error && !loading && isReady && (
                  <video
                    ref={videoRef}
                    autoPlay
                    muted
                    playsInline
                    className="tutorial-camera-video"
                  />
                )}
              </div>
            </div>
          </div>
        )}

        {/* 텍스트 영역 */}
        <div
          className={`tutorial-title ${
            isStep1 ? 'tutorial-title--center' : 'tutorial-title--side'
          }`}
        >
          {error ? (
            <>
              카메라 연결에 문제가 있습니다.
              <br />
              담당자에게 알려 주세요.
            </>
          ) : (
            renderTitle()
          )}
        </div>
      </div>
    </div>
  );
}

export default TutorialPage;
