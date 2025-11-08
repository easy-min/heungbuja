import './TutorialPage.css';

function TutorialPage(){
    return <>
    <div className="tutorial-page">
      <p> 버튼을 눌러 체조를 시작하세요! </p>
      <button className="play-btn">
        <div className="play-icon" />
      </button>
    </div>
    </>
}

export default TutorialPage;