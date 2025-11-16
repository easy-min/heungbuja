import { useNavigate, useLocation } from "react-router-dom";
import type { GameEndResponse } from '@/types/game';
import StarBurst from '@/components/StarBurst';
import './ResultPage.css';

function ResultPage(){
  const nav = useNavigate();
  const location = useLocation();

  const state = location.state as GameEndResponse | undefined;

  const finalScore = state?.finalScore ?? 100;
  const message = state?.message ?? '흥부와의 체조, 즐거우셨나요?';

  return <>
    <div className="result-page">
      <div className="result-info">
        <StarBurst />
        <p className="result-message">{message}</p>
        <p className="result-score">{finalScore.toFixed(0)} 점</p>
      </div>
      <div className="btn-container">
        <button className="play-btn" onClick={() => nav("/tutorial")}>
          <p>다시하기</p>
        </button>
        <button className="play-btn" onClick={() => nav("/home")}>
          <p>홈으로</p>
        </button>
      </div>
    </div>
  </>
}

export default ResultPage;