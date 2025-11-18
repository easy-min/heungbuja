#!/bin/bash
# 라즈베리파이 배포 스크립트

# 설정
RASPBERRY_PI_HOST="a103@raspberrypi.local"       # 라즈베리파이 주소
RASPBERRY_PI_PATH="/home/a103/S13P31A103"   # 라즈베리파이 프로젝트 경로

# 색상 출력
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🍓 라즈베리파이 배포 스크립트${NC}"
echo ""

# 1. 프론트엔드 빌드
echo -e "${YELLOW}[1/4]${NC} 프론트엔드 빌드 중..."
cd ../frontend
npm run build

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 빌드 실패${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 빌드 완료${NC}"
echo ""

# 2. dist 폴더 전송
echo -e "${YELLOW}[2/4]${NC} dist 폴더 전송 중..."
scp -r dist/ "$RASPBERRY_PI_HOST:$RASPBERRY_PI_PATH/frontend/"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ dist 전송 실패${NC}"
    echo "라즈베리파이 연결을 확인하세요."
    exit 1
fi

echo -e "${GREEN}✅ dist 전송 완료${NC}"
echo ""

# 3. raspberry 폴더 전송
echo -e "${YELLOW}[3/4]${NC} raspberry 폴더 전송 중..."
cd ../raspberry
scp -r . "$RASPBERRY_PI_HOST:$RASPBERRY_PI_PATH/raspberry/"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ raspberry 폴더 전송 실패${NC}"
    exit 1
fi

echo -e "${GREEN}✅ raspberry 폴더 전송 완료${NC}"
echo ""

# 4. 실행 권한 부여 및 서버 재시작
echo -e "${YELLOW}[4/4]${NC} 라즈베리파이에서 설정 중..."
ssh "$RASPBERRY_PI_HOST" << 'EOF'
cd /home/a103/S13P31A103/raspberry
chmod +x start.sh

# 기존 프로세스 종료
pkill -f "local-server.js"
pkill -f "serve.*dist"

echo "서버를 재시작하려면 라즈베리파이에서 다음 명령을 실행하세요:"
echo "  cd /home/a103/S13P31A103/raspberry"
echo "  ./start.sh"
EOF

echo ""
echo -e "${GREEN}🎉 배포 완료!${NC}"
echo ""
echo "라즈베리파이에서 서버를 시작하세요:"
echo "  ssh $RASPBERRY_PI_HOST"
echo "  cd $RASPBERRY_PI_PATH/raspberry"
echo "  ./start.sh"
