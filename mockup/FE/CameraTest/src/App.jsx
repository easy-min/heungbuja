import { useEffect, useRef, useState } from 'react';
import './App.css';

function App() {
    const videoRef = useRef(null);
    const [error, setError] = useState('');

    useEffect(() => {
        // 카메라 시작
        const startCamera = async () => {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ 
                    video: true 
                });
                
                if (videoRef.current) {
                    videoRef.current.srcObject = stream;
                }
            } catch (err) {
                setError('카메라 접근 실패: ' + err.message);
            }
        };

        startCamera();

        // 정리
        return () => {
            if (videoRef.current?.srcObject) {
                videoRef.current.srcObject.getTracks().forEach(track => track.stop());
            }
        };
    }, []);
    const stopCamera = () => {
    videoRef.current?.srcObject?.getTracks().forEach(track => track.stop());
};
    return (
        <div className="app">
            <h1>카메라 테스트</h1>
            {error && <p style={{ color: 'red' }}>{error}</p>}

            <video 
                ref={videoRef} 
                autoPlay 
                playsInline
                style={{ width: '100%', maxWidth: '640px' }}
            />
            
        <button onClick={stopCamera}>카메라 끄기</button>
        </div>
    );
}

export default App;