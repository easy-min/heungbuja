import { useEffect } from 'react';
import { useDeviceAuth } from '../hooks/useDeviceAuth';
import './RaspberryLoginPage.css';

interface RaspberryLoginPageProps {
    deviceId: string;
}

function RaspberryLoginPage({ deviceId }: RaspberryLoginPageProps) {
    const { autoLogin, error } = useDeviceAuth();

    useEffect(() => {
        autoLogin(deviceId);
    }, [deviceId]);

    return (
        <div className="raspberry-login-page">
            <div className="auth-container">
                <div className="spinner"></div>
                <h2>기기 인증 중...</h2>
                <p className="device-id">Device ID: {deviceId}</p>
                {error && (
                    <div className="error-message">
                        {error}
                    </div>
                )}
            </div>
        </div>
    );
}

export default RaspberryLoginPage;