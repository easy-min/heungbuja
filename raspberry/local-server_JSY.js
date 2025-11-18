// --- 1. 필요한 라이브러리 가져오기 ---
const express = require("express");
const { execSync } = require("child_process");
const cors = require("cors");
const path = require("path");
const axios = require("axios");
const { jwtDecode } = require("jwt-decode");

// --- 2. 전역 변수 및 상수 설정 ---
const app = express();
const PORT = 3001;
const API_BASE_URL = "https://heungbuja.site/api";

let userAccessToken = null;
let tokenRefreshTimer = null;
let sseClients = [];

// --- 3. 미들웨어 설정 ---
app.use(cors());
app.use(express.json());

// --- 4. 헬퍼 함수 ---
function getDeviceSerial() {
  try {
    // 터미널에서 직접 실행하는 것과 동일한 방식으로 기기 번호를 가져옴
    const command = "cat /proc/cpuinfo | grep Serial | awk '{print $3}'";
    const serial = execSync(command).toString().trim();

    if (serial) {
      console.log(`[INFO] 기기 번호 읽기 성공: ${serial}`);
      return serial;
    } else {
      throw new Error("명령어 실행 결과가 비어있습니다.");
    }
  } catch (error) {
    console.error("CRITICAL: 기기 번호를 읽을 수 없습니다.", error);
    return null;
  }
}

// --- 5. 인증 관련 핵심 로직 ---
async function refreshTokenAndScheduleNext() {
  const serialNumber = getDeviceSerial();
  if (!serialNumber) {
    console.error("[인증 오류] 기기 번호가 없어 로그인을 시도할 수 없습니다. 30초 후 재시도합니다.");
    setTimeout(refreshTokenAndScheduleNext, 30 * 1000);
    return;
  }
  try {
    console.log("[인증] AWS 백엔드에 기기 로그인을 시도합니다...");

    // 백엔드가 기대하는 'deviceNumber'로 key 이름 변경
    const response = await axios.post(`${API_BASE_URL}/auth/device`, {
      serialNumber: serialNumber,
    });

    if (response.data && response.data.accessToken) {
      userAccessToken = response.data.accessToken;
      console.log("[인증] 인증 토큰을 성공적으로 발급/갱신했습니다.");
      scheduleNextRefresh();
    } else {
      throw new Error("응답 데이터에 accessToken이 없습니다.");
    }
  } catch (error) {
    // 백엔드에서 오는 상세 에러 메시지를 로그에 포함
    const errorMessage = error.response ? JSON.stringify(error.response.data) : error.message;
    console.error(`[인증 오류] 토큰 발급/갱신 실패: ${errorMessage}`);
    console.error("30초 후 재시도합니다.");
    setTimeout(refreshTokenAndScheduleNext, 30 * 1000);
  }
}

function scheduleNextRefresh() {
  if (tokenRefreshTimer) clearTimeout(tokenRefreshTimer);
  try {
    const decodedToken = jwtDecode(userAccessToken);
    const expiresIn = decodedToken.exp * 1000 - Date.now();
    const refreshBuffer = 5 * 60 * 1000; // 5분 버퍼
    const refreshIn = expiresIn - refreshBuffer;

    if (refreshIn > 0) {
      tokenRefreshTimer = setTimeout(refreshTokenAndScheduleNext, refreshIn);
      console.log(`[인증] 다음 토큰 갱신은 약 ${Math.round(refreshIn / 60000)}분 뒤에 실행됩니다.`);
    } else {
      refreshTokenAndScheduleNext();
    }
  } catch (error) {
    console.error("[인증 오류] 토큰 해석 실패:", error.message);
  }
}

// --- 6. API 엔드포인트 정의 ---
app.get("/api/get-token", (req, res) => {
  if (userAccessToken) {
    res.status(200).json({ token: userAccessToken });
  } else {
    res.status(404).json({ error: "토큰이 아직 준비되지 않았습니다." });
  }
});

app.get("/api/events", (req, res) => {
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
  res.flushHeaders();
  const clientId = Date.now();
  sseClients.push({ id: clientId, res });
  console.log(`[SSE] 클라이언트 연결됨: ${clientId}`);
  req.on("close", () => {
    sseClients = sseClients.filter((client) => client.id !== clientId);
    console.log(`[SSE] 클라이언트 연결 끊김: ${clientId}`);
  });
});

app.post("/api/wakeword-detected", (req, res) => {
  console.log("[SSE] Wake Word 감지 신호 수신! 프론트엔드로 이벤트를 전송합니다.");
  sseClients.forEach((client) => client.res.write(`data: ${JSON.stringify({ event: "wakeword" })}\n\n`));
  res.sendStatus(200);
});

// --- 7. 프론트엔드 정적 파일 서빙 ---
app.use(express.static(path.join(__dirname, "../frontend/dist")));
app.get(/^(?!\/api).*/, (req, res) => {
  res.sendFile(path.join(__dirname, "../frontend/dist/index.html"));
});

// --- 8. 서버 실행 ---
app.listen(PORT, "0.0.0.0", () => {
  console.log(`'흥부자' 로컬 서버가 포트 ${PORT}에서 실행되었습니다.`);
  refreshTokenAndScheduleNext();
});
