import { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { gameStartApi } from '@/api/game';
import { useGameStore } from '@/store/gameStore';
import type { GameStartResponse } from '@/types/game';
import './TutorialPage.css';

function TutorialPage(){
  const nav = useNavigate();
  const location = useLocation();
  const setFromApi = useGameStore(s => s.setFromApi);
  const [loading, setLoading] = useState(true);
  const [songId, setSongId] = useState(0);

  // 페이지 진입 시 게임 데이터 로드 (음성 명령 처리 포함)
  useEffect(() => {
    setLoading(true);

    // 음성 명령으로 전달된 게임 데이터 확인
    const voiceCommandData = location.state as GameStartResponse | undefined;

    if (voiceCommandData?.gameInfo) {
      // 음성 명령으로 데이터를 받은 경우 - API 호출 없이 바로 저장
      console.log('음성 명령으로 받은 게임 데이터를 store에 저장:', voiceCommandData);
      setFromApi(voiceCommandData);
      setLoading(false);
      setSongId(voiceCommandData.gameInfo.songId);
    } else {
      // 일반 진입인 경우 - API 호출해서 목 데이터 가져오기
      const initGameData = async () => {
        try {
          const res = await gameStartApi();
          setFromApi(res);
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

  const handleStart = () => {
    if (loading) return; // 로딩 중이면 무시
    // useEffect에서 이미 데이터를 로드했으므로 바로 게임 페이지로 이동
    nav(`/game/${songId}`);
  };

  return <>
    <div className="tutorial-page">
      <p > 버튼을 눌러 체조를 시작하세요! </p>
      <div className="info-msg"> ( 원할한 진행을 위해 카메라 허용을 미리 체크해주세요 ) </div>
      <button className="play-btn" onClick={handleStart} disabled={loading}>
        {loading ? '로딩 중...' : <div className="play-icon" />}
      </button>
    </div>
  </>
}

export default TutorialPage;