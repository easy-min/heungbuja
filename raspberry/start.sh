#!/bin/bash
# Raspberry Pi 자동 시작 스크립트

# 스크립트 경로 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
FRONTEND_DIST="$PROJECT_ROOT/frontend/dist"

echo "🍓 흥부자 라즈베리파이 서버 시작..."

# local-server 실행 (포트 3001 - 기기 시리얼 번호 API)
echo "📡 로컬 서버 시작 (포트 3001)..."
node "$SCRIPT_DIR/local-server.js" &
LOCAL_SERVER_PID=$!

# 잠시 대기 (로컬 서버 초기화)
sleep 2

# 프론트엔드 정적 파일 서빙 (포트 5173)
echo "🌐 프론트엔드 서버 시작 (포트 5173)..."
npx serve -s "$FRONTEND_DIST" -l 5173 &
FRONTEND_PID=$!

echo "✅ 서버 시작 완료!"
echo "   - 로컬 API: http://localhost:3001"
echo "   - 프론트엔드: http://localhost:5173/user"
echo ""
echo "종료하려면 Ctrl+C를 누르세요."

# 종료 시그널 처리
trap "echo '🛑 서버 종료 중...'; kill $LOCAL_SERVER_PID $FRONTEND_PID; exit 0" INT TERM

# 백그라운드 프로세스 대기
wait
