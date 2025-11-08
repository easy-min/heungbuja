import { useNavigate } from "react-router-dom";
import './TutorialPage.css';

function TutorialPage(){
  const nav = useNavigate();
  return <>
    <div className="tutorial-page">
      <p> 버튼을 눌러 체조를 시작하세요! </p>
      <button className="play-btn" onClick={() => nav("/game/test-song")}>
        <div className="play-icon" />
      </button>
    </div>
  </>
}

export default TutorialPage;