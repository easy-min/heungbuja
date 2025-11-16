# 🍓 라즈베리파이 배포 가이드

흥부자 앱을 라즈베리파이에 배포하기 위한 가이드입니다.

## 📋 사전 요구사항

### 라즈베리파이
- Raspberry Pi 3 이상
- Raspberry Pi OS (Debian 기반)
- Node.js 14 이상
- 네트워크 연결

### 개발 PC
- Git
- Node.js & npm
- SSH 접근 가능

## 🚀 배포 방법

### 1. 최초 설정 (라즈베리파이에서)

```bash
# Node.js 설치 확인
node --version
npm --version

# 프로젝트 클론 또는 복사
cd /home/pi
git clone <repository-url> S13P31A103
# 또는 USB/scp로 프로젝트 복사

# serve 패키지 설치 (정적 파일 서빙용)
npm install -g serve

# 의존성 설치 (local-server.js용)
cd S13P31A103/raspberry
npm install express cors
```

### 2. 배포 (개발 PC에서)

```bash
# 배포 스크립트 실행
cd S13P31A103/raspberry
chmod +x deploy.sh
./deploy.sh
```

배포 스크립트는 자동으로:
1. 프론트엔드 빌드 (`npm run build`)
2. `dist/` 폴더를 라즈베리파이로 전송
3. `raspberry/` 폴더를 라즈베리파이로 전송
4. 실행 권한 설정

### 3. 서버 시작 (라즈베리파이에서)

```bash
cd /home/pi/S13P31A103/raspberry
./start.sh
```

서버가 시작되면:
- 로컬 API 서버: `http://localhost:3001`
- 프론트엔드 앱: `http://localhost:5173/user`

## 🔧 수동 배포 방법

자동 스크립트를 사용할 수 없는 경우:

### 개발 PC에서

```bash
# 1. 빌드
cd frontend
npm run build

# 2. 파일 전송
scp -r dist/ pi@raspberrypi.local:/home/pi/S13P31A103/frontend/
scp -r raspberry/ pi@raspberrypi.local:/home/pi/S13P31A103/
```

### 라즈베리파이에서

```bash
# 1. 의존성 설치
cd /home/pi/S13P31A103/raspberry
npm install express cors

# 2. 서버 시작
./start.sh
```

## ⚙️ 자동 시작 설정 (선택사항)

부팅 시 자동으로 서버를 시작하려면:

### systemd 서비스 생성

```bash
sudo nano /etc/systemd/system/heungbuja.service
```

다음 내용 입력:

```ini
[Unit]
Description=Heungbuja Raspberry Pi Server
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/S13P31A103/raspberry
ExecStart=/bin/bash /home/pi/S13P31A103/raspberry/start.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

서비스 활성화:

```bash
sudo systemctl daemon-reload
sudo systemctl enable heungbuja.service
sudo systemctl start heungbuja.service

# 상태 확인
sudo systemctl status heungbuja.service
```

## 📁 파일 구조

```
S13P31A103/
├── frontend/
│   └── dist/              # 빌드된 정적 파일
│       ├── index.html
│       └── assets/
└── raspberry/
    ├── local-server.js    # 기기 시리얼 번호 API (포트 3001)
    ├── start.sh          # 서버 시작 스크립트
    ├── deploy.sh         # 배포 자동화 스크립트
    └── README.md         # 이 파일
```

## 🔍 트러블슈팅

### 포트가 이미 사용 중인 경우

```bash
# 포트 사용 프로세스 확인
lsof -i :3001
lsof -i :5173

# 프로세스 종료
kill -9 <PID>
```

### 권한 오류

```bash
chmod +x /home/pi/S13P31A103/raspberry/start.sh
```

### 시리얼 번호를 읽을 수 없는 경우

```bash
# /proc/cpuinfo 확인
cat /proc/cpuinfo | grep Serial
```

### 빌드 파일이 없는 경우

```bash
# 개발 PC에서 먼저 빌드
cd frontend
npm run build
```

## 🌐 접속 방법

### 라즈베리파이 로컬 브라우저
```
http://localhost:5173/user
```

### 같은 네트워크의 다른 기기에서
```
http://<라즈베리파이-IP>:5173/user
```

라즈베리파이 IP 확인:
```bash
hostname -I
```

## 📝 참고사항

- **local-server.js**: 라즈베리파이의 하드웨어 시리얼 번호를 읽어 기기 인증에 사용
- **포트 3001**: 기기 시리얼 번호 API
- **포트 5173**: 프론트엔드 웹 서버
- localStorage에 토큰이 저장되어 재부팅 후에도 자동 로그인됩니다

## 🆘 문제 발생 시

1. 서버 로그 확인
2. 네트워크 연결 확인
3. Node.js 버전 확인 (`node --version`)
4. 포트 충돌 확인
5. 파일 권한 확인

더 자세한 정보는 프로젝트 메인 README를 참고하세요.
