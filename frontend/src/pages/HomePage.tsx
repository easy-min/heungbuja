import React from 'react';
import { useNavigate } from 'react-router-dom';
import './HomePage.css';

const HomePage: React.FC = () => {
  const navigate = useNavigate();

  const handleMusicClick = () => {
    navigate('/listening');
  };

  const handleExerciseClick = () => {
    navigate('/game');
  };

  return (
    <div className="home-page">
      <div className="home-container">
        <div className="button-container">
          <button 
            className="home-button music-button"
            onClick={handleMusicClick}
          >
            <div className="button-content">
              <img 
                src="/character-music.svg" 
                alt="노래 감상" 
                className="character-image"
              />
              <span className="button-text">노래 감상</span>
            </div>
          </button>

          <button 
            className="home-button exercise-button"
            onClick={handleExerciseClick}
          >
            <div className="button-content">
              <img 
                src="/character-exercise.svg" 
                alt="음악 체조" 
                className="character-image"
              />
              <span className="button-text">음악 체조</span>
            </div>
          </button>
        </div>
      </div>

    </div>
  );
};

export default HomePage;