import { useNavigate } from "react-router-dom";
import './TutorialPage.css';

function TutorialPage(){
  const nav = useNavigate();
  return <>
    <div className="tutorial-page">
      <p > 버튼을 눌러 체조를 시작하세요! </p>
      <div className="info-msg"> ( 원할한 진행을 위해 카메라 허용을 미리 체크해주세요 ) </div>
      <button className="play-btn" onClick={() => nav("/game/test-song")}>
        <div className="play-icon" />
      </button>
    </div>
  </>
}

export default TutorialPage;