import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { gameStartApi } from '@/api/game';
import { useGameStore } from '@/store/gameStore';
import './TutorialPage.css';

function TutorialPage(){
  const nav = useNavigate();
  const setFromApi = useGameStore(s => s.setFromApi);
  const [loading, setLoading] = useState(false);

  // 페이지 진입 시 자동으로 게임 데이터 로드
  useEffect(() => {
    const initGameData = async () => {
      try {
        const res = await gameStartApi();
        setFromApi(res);
        console.log('게임 데이터 로드 완료:', res);
      } catch (e) {
        console.error('게임 데이터 로드 실패:', e);
      }
    };
    initGameData();
  }, [setFromApi]);

  const handleStart = async () => {
    if (loading) return;
    setLoading(true);
    try {
      const res = await gameStartApi();
      setFromApi(res);

      nav('/game/test-song');
      // nav(`/game/${d.songId}`);
    } catch (e) {
      console.error('game info 로딩 실패', e);
    } finally {
      setLoading(false);
    }
  };

  return <>
    <div className="tutorial-page">
      <p > 버튼을 눌러 체조를 시작하세요! </p>
      <div className="info-msg"> ( 원할한 진행을 위해 카메라 허용을 미리 체크해주세요 ) </div>
      <button className="play-btn" onClick={handleStart} disabled={loading}>
        <div className="play-icon" />
      </button>
    </div>
  </>
}

export default TutorialPage;