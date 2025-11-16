// src/utils/deviceDetector.ts
const LOCAL_API_URL = 'http://localhost:3001';
// 'http://192.168.53.162:3001';

interface DeviceCheckResult {
    isRaspberryPi: boolean;
    deviceId?: string;
}

export const checkIfRaspberryPi = async (): Promise<DeviceCheckResult> => {
    try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 2000); // 2초 타임아웃

        const response = await fetch(`${LOCAL_API_URL}/api/device-serial`, {
            method: 'GET',
            signal: controller.signal
        });

        clearTimeout(timeoutId);
        
        if (response.ok) {
            const data = await response.json();
            return {
                isRaspberryPi: true,
                deviceId: data.deviceId  // 시리얼 번호 포함
            };
        }
    } catch (error) {
        // 로컬 서버 접속 실패 = 웹 버전
        console.log('Local server not found - Web version');
    }
    
    return {
        isRaspberryPi: false,
        deviceId: undefined
    };
};