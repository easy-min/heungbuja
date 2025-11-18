#!/bin/bash
# Raspberry Pi 자동 시작 스크립트

# 스크립트 경로 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🍓 흥부자 라즈베리파이 서버 시작..."

# local-server 실행 (포트 3001 - API + 프론트엔드 통합)
echo "📡 통합 서버 시작 (포트 3001)..."
node "$SCRIPT_DIR/local-server.js" &
SERVER_PID=$!

echo "✅ 서버 시작 완료!"
echo "   - 접속 URL: http://localhost:3001/user"
echo ""
echo "종료하려면 Ctrl+C를 누르세요."

# 종료 시그널 처리
trap "echo '🛑 서버 종료 중...'; kill $SERVER_PID; exit 0" INT TERM

# 백그라운드 프로세스 대기
wait
