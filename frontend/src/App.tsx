import { BrowserRouter, Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import GamePage from './pages/GamePage';
import UserLoginPage from './pages/UserLoginPage';
import TutorialPage from './pages/TutorialPage';
import ResultPage from './pages/ResultPage';
import SongPage from './pages/SongPage';
import './index.css';

function App() {
  return (
    <BrowserRouter basename="/user">
      <div className="app">
        <Routes>
          <Route path="/" element={<UserLoginPage />} />
          <Route path="/home" element={<HomePage />} />
          <Route path="/listening" element={<SongPage />} />
          <Route path="/tutorial" element={<TutorialPage />} />
          <Route path="/game/:songId" element={<GamePage />} />
          <Route path="/result" element={<ResultPage />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}

export default App;