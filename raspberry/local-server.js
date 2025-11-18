// local-server.js
const express = require('express');
const fs = require('fs');
const cors = require('cors');
const axios = require('axios');

const app = express();
app.use(cors());
app.use(express.json());

// === 설정 ===
const BACKEND_URL = 'https://heungbuja.site/api';
const TOKEN_FILE_PATH = '/home/a103/main_service/token.txt';

// === 토큰 저장 ===
let currentAccessToken = null;
let currentRefreshToken = null;
let tokenExpiryTime = null;
let sseClients = [];

// === 기기 번호 가져오기 ===
function getDeviceSerial() {
    try {
        const cpuinfo = fs.readFileSync('/proc/cpuinfo', 'utf8');
        const serialLine = cpuinfo.split('\n').find(line => line.includes('Serial'));
        return serialLine ? serialLine.split(':')[1].trim() : null;
    } catch (error) {
        console.error('❌ 기기 번호 읽기 실패:', error);
        return null;
    }
}

// === 로그인 ===
async function loginWithDeviceId() {
    try {
        const deviceId = getDeviceSerial();
        if (!deviceId) {
            console.error('❌ 기기 번호를 찾을 수 없습니다');
            return false;
        }

        console.log('🔐 기기 번호로 로그인 중...', deviceId);

        const response = await axios.post(`${BACKEND_URL}/auth/device`, {
            deviceId: deviceId
        });

        currentAccessToken = response.data.accessToken;
        currentRefreshToken = response.data.refreshToken;

        const expiresIn = response.data.expiresIn || 3600;
        tokenExpiryTime = Date.now() + (expiresIn * 1000);

        fs.writeFileSync(TOKEN_FILE_PATH, currentAccessToken);

        console.log('✅ 로그인 성공! 토큰 저장 완료');
        console.log('📅 토큰 만료 시간:', new Date(tokenExpiryTime).toLocaleString());
        return true;

    } catch (error) {
        console.error('❌ 로그인 실패:', error.response?.data || error.message);
        return false;
    }
}

// === 토큰 갱신 ===
async function refreshAccessToken() {
    try {
        console.log('🔄 토큰 갱신 중...');

        const response = await axios.post(`${BACKEND_URL}/auth/refresh`, {
            refreshToken: currentRefreshToken
        });

        currentAccessToken = response.data.accessToken;
        const expiresIn = response.data.expiresIn || 3600;
        tokenExpiryTime = Date.now() + (expiresIn * 1000);

        fs.writeFileSync(TOKEN_FILE_PATH, currentAccessToken);

        console.log('✅ 토큰 갱신 완료');
        console.log('📅 새 만료 시간:', new Date(tokenExpiryTime).toLocaleString());
        return true;

    } catch (error) {
        console.error('❌ 토큰 갱신 실패:', error.response?.data || error.message);
        console.log('🔄 재로그인 시도...');
        return await loginWithDeviceId();
    }
}

// === 토큰 만료 체크 (1분마다) ===
setInterval(async () => {
    if (!tokenExpiryTime) {
        console.log('⚠️ 토큰이 없습니다. 로그인 시도...');
        await loginWithDeviceId();
        return;
    }

    const fiveMinutesBeforeExpiry = tokenExpiryTime - (5 * 60 * 1000);
    if (Date.now() >= fiveMinutesBeforeExpiry) {
        console.log('⏰ 토큰 만료 임박, 갱신 시작...');
        await refreshAccessToken();
    }
}, 60000);

// === SSE 엔드포인트 ===
app.get('/api/voice-events', (req, res) => {
    console.log('🔗 SSE 클라이언트 연결됨');

    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no');

    res.write('data: {"type":"CONNECTED"}\n\n');
    sseClients.push(res);

    req.on('close', () => {
        console.log('❌ SSE 클라이언트 연결 종료');
        sseClients = sseClients.filter(client => client !== res);
    });
});

// === 웨이크워드 감지 알림 ===
app.post('/api/wakeword-detected', (req, res) => {
    console.log('🎤 웨이크워드 감지됨! SSE 이벤트 전송...');

    sseClients.forEach(client => {
        client.write('data: {"type":"WAKE_WORD_DETECTED"}\n\n');
    });

    res.json({ success: true });
});

// === 재녹음 요청 ===
app.post('/api/retry-recording', (req, res) => {
    console.log('🔁 재녹음 요청됨');

    try {
        fs.writeFileSync('/home/a103/main_service/retry_flag.txt', 'retry');
        console.log('✅ 재녹음 플래그 파일 생성 완료');
        res.json({ success: true });
    } catch (error) {
        console.error('❌ 재녹음 플래그 파일 생성 실패:', error);
        res.status(500).json({ error: 'Failed to create retry flag' });
    }
});

// === 토큰 제공 (main.py용) ===
app.get('/api/token', (req, res) => {
    if (!currentAccessToken) {
        return res.status(401).json({ error: 'No token available. Please wait for login.' });
    }

    if (Date.now() >= tokenExpiryTime) {
        return res.status(401).json({ error: 'Token expired' });
    }

    res.json({
        token: currentAccessToken,
        expiryTime: tokenExpiryTime
    });
});

// === 토큰 제공 (프론트용) ===
app.get('/api/frontend-token', (req, res) => {
    if (!currentAccessToken) {
        return res.status(401).json({ error: 'No token available' });
    }

    res.json({
        accessToken: currentAccessToken,
        refreshToken: currentRefreshToken,
        expiryTime: tokenExpiryTime
    });
});

// === 기기번호 조회 ===
app.get('/api/device-serial', (req, res) => {
    const serial = getDeviceSerial();
    if (serial) {
        res.json({ deviceId: serial });
    } else {
        res.status(404).json({ error: 'Serial number not found' });
    }
});

// === 서버 시작 ===
const PORT = 3001;
app.listen(PORT, '0.0.0.0', async () => {
    console.log(`🚀 Local API server running on port ${PORT}`);
    console.log('⏳ 로그인 시작...');

    const loginSuccess = await loginWithDeviceId();

    if (loginSuccess) {
        console.log('✅ 서버 준비 완료!');
    } else {
        console.log('⚠️ 로그인 실패. 1분 후 재시도합니다.');
    }
});
