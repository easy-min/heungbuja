import { BrowserRouter, Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import GamePage from './pages/GamePage';
import TutorialPage from './pages/TutorialPage';
import ResultPage from './pages/ResultPage';
import SongPage from './pages/SongPage';
import './index.css';
import './App.css';
import { useEffect, useState } from 'react';
import { checkIfRaspberryPi } from './utils/deviceDetector';
import RaspberryLoginPage from './pages/RaspberryLoginPage';
import WebLoginPage from './pages/WebLoginPage';

function App() {
    const [isChecking, setIsChecking] = useState<boolean>(true);
    const [isRaspberryPi, setIsRaspberryPi] = useState<boolean>(false);
    const [deviceId, setDeviceId] = useState<string | undefined>(undefined);

    useEffect(() => {
        detectEnvironment();
    }, []);

    const detectEnvironment = async () => {
        try {
            const result = await checkIfRaspberryPi();
            setIsRaspberryPi(result.isRaspberryPi);
            setDeviceId(result.deviceId);
        } catch (error) {
            console.error('Environment detection error:', error);
            setIsRaspberryPi(false);
        } finally {
            setIsChecking(false);
        }
    };

    // 환경 체크 중
    if (isChecking) {
        return (
            <div className="app-loading">
                <div className="loading-container">
                    <div className="spinner"></div>
                    <h2>환경 확인 중...</h2>
                    <p className="loading-text">잠시만 기다려주세요</p>
                </div>
            </div>
        );
    }
  return (
    <BrowserRouter>
      <div className="app">
        <Routes>
           {/* 로그인 페이지 - 환경에 따라 다른 컴포넌트 */}
            <Route 
                path="/" 
                element={isRaspberryPi ? <RaspberryLoginPage deviceId={deviceId!} /> : <WebLoginPage />} 
            />
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