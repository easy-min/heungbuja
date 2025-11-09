import { useNavigate } from "react-router-dom";
import './ResultPage.css';

function ResultPage(){
  const nav = useNavigate();
  return <>
    <div className="result-page">
      <p> 흥겹고 건강한 체조 끝! </p>
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