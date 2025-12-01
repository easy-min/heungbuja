# 🎵 흥부자 (興夫者)

### 어르신을 위한 음성 기반 헬스케어 엔터테인먼트 플랫폼

*복잡한 터치 없이, 목소리만으로 옛날 노래 들으며 건강하게 체조하세요*

**SSAFY 13기 자율 프로젝트 A103팀**

---

## 📑 목차

- [🎯 프로젝트 소개](#-프로젝트-소개)
- [✨ 주요 기능](#-주요-기능)
- [👥 팀 구성](#-팀-구성)
- [🛠️ 기술 스택](#️-기술-스택)
- [🏗️ 시스템 아키텍처](#️-시스템-아키텍처)
- [💡 핵심 기술](#-핵심-기술)
- [📈 성과](#-성과)
- [🚀 시작하기](#-시작하기)

---

## 🎯 프로젝트 소개

### 배경

> *"스마트폰은 어렵고, 버튼은 작고, 메뉴는 복잡하고..."*

대한민국 65세 이상 고령 인구는 **1,000만 명**을 돌파했습니다. 하지만 기존 헬스케어 서비스는 복잡한 터치 조작과 작은 글씨로 어르신들에게 **디지털 장벽**이 되고 있습니다.

### 해결책

**흥부자**는 음성만으로 모든 것을 제어할 수 있는 혁신적인 플랫폼입니다.

| 음성 명령 | 결과 |
|:---:|:---:|
| "트와이스 노래 틀어줘" | 🎵 음악 재생 |
| "체조하고 싶어" | 🤸 게임 시작 |
| "일시정지" | ⏸️ 즉시 정지 |
| "도와주세요" | 🚨 응급 신고 |

### 📊 복지관 유저 테스트 결과

| 만족도 | 재사용 의향 |
|:---:|:---:|
| **4.6 / 5.0** | **87.5%** |

---

## ✨ 주요 기능

### 🎤 1. MCP 기반 음성 명령 처리

**📋 기획 배경**

- **기존 방식**: Intent 분류 모델 → 성공률 60%
- **문제점**: 학습 데이터 부족, 복합 명령 처리 불가
- **해결**: GPT가 Tool을 자동 선택하는 MCP 아키텍처 도입

**기술적 구현**

```
[1] Tool 정의 (8가지)
    → search_song, start_game, control_playback, handle_emergency 등

[2] GPT 라우팅
    → Few-shot Learning + Step-by-Step 프롬프트
    → Context 기반 판단 (현재 모드, 재생 상태, 응급 상태)

[3] 비즈니스 로직 실행
    → Service 계층 호출, Redis 상태 관리, WebSocket 알림
```

**핵심 최적화**

| 최적화 항목 | 내용 | 성과 |
|---|---|---|
| 재시도 로직 | GPT API 실패 시 3회 자동 재시도 | 52.94% → **98.39%** |
| 템플릿 응답 | GPT 호출 70% 절감 | 비용 최적화 |
| SHA256 해시 캐싱 | TTS API 호출 92% 절감 | 응답 속도 **88.5%** 단축 |

---

### 🤖 2. GCN+CNN 하이브리드 동작 인식 AI

**📋 기획 배경**

- **CNN 단독**: 공간 관계(뼈대 구조) 학습 약함
- **해결**: GCN(그래프 합성곱) + CNN(시간적 패턴) 결합

**모델 구조**

```
입력: 8프레임 × 22개 랜드마크 (Mediapipe Pose)
           ↓
┌─────────────────────────────┐
│      GCN Layer 2층          │  ← 어깨→팔꿈치→손목 그래프 구조 학습
│  POSE_CONNECTIONS 기반       │
└─────────────────────────────┘
           ↓
┌─────────────────────────────┐
│      Temporal CNN           │  ← 8프레임 시퀀스로 시간적 흐름 학습
│  1D Conv + Global Avg Pool  │
└─────────────────────────────┘
           ↓
출력: 7가지 동작 분류 (CLAP, STRETCH, TILT 등)
```

**학습 데이터**

| 데이터셋 | 개수 | 설명 |
|---|---:|---|
| forTrain | 504개 | 정적 이미지, 스튜디오 촬영 |
| brandnewTrain | 2,453개 | 동영상 추출, 실전 환경 |
| 게임 데이터 Fine-tuning | 713개 | 실제 플레이 |
| **총합** | **3,670개** | 시퀀스 |

**클래스 불균형 해결**: Inverse Frequency Weighting 적용

---

### 🚨 3. 응급 상황 자동 처리 시스템

**📋 기획 배경**

- 독거노인 증가, 응급 상황 대응 필요
- 복잡한 조작 불필요, 음성만으로 신고

**처리 플로우**

```
"도와주세요" 음성 감지
        ↓
handle_emergency Tool 실행
        ↓
Redis 응급 상태 저장 (PENDING)
        ↓
"괜찮으세요?" TTS 재생
        ↓
ScheduledExecutor 60초 타이머 시작
        ↓
┌───────────────────────────────────────┐
│  "괜찮아"    → cancel_emergency (취소)   │
│  "안 괜찮아"  → confirm_emergency (즉시) │
│  60초 무응답 → 자동 확정                 │
└───────────────────────────────────────┘
        ↓
WebSocket → 관리자 실시간 알림
```

---

### 🎵 4. AI 자동 음악 분석 시스템

| 기능 | 설명 |
|---|---|
| BPM 자동 추출 | Librosa 라이브러리 |
| 비트 타이밍 분석 | 동작 타이밍 자동 계산 |
| 난이도 자동 설정 | BPM 기반 Lv1/Lv2/Lv3 생성 |
| MongoDB 캐싱 | 분석 결과 영구 저장 |

**성과**: 노래 1곡당 분석 시간 평균 **10초** (수작업 대비 10배 향상)

---

## 👥 팀 구성

| 역할 | 이름 | 담당 업무 |
|:---:|:---:|---|
| **Backend Lead** | 신해봄 | Motion AI 서버, Spring Backend (13개 도메인), 음성 처리, 응급 시스템 |
| **Frontend Lead** | 홍길동 | React UI/UX, 음성 인터페이스, 게임 화면 |
| **AI** | 김철수 | AI 모델 개발 |
| **Infra** | 이영희 | Docker, Jenkins, AWS 배포 |
| **Game** | 박민수 | 게임 로직, Unity 연동 |
| **Music** | 최지우 | 음악 분석, MongoDB 관리 |

---

## 🛠️ 기술 스택

### Frontend

React 18.2.0 | TypeScript 5.0 | Vite 5.0

### Backend

Spring Boot 3.5.7 | Java 17 | JPA/Hibernate | Redis 7.0 | MySQL 8.0 | WebSocket/STOMP

### AI / ML

Python 3.11 | PyTorch 2.0 | FastAPI 0.104 | Mediapipe 0.10

### External API

OpenAI GPT-4 | Whisper STT | TTS Nova

### Infra

AWS EC2/S3 | Docker 24.0 | Jenkins CI/CD | Nginx 1.24

### Database

MongoDB 7.0 | Redis 7.0 | MySQL 8.0

### 협업 도구

GitLab | Jira | Notion | Figma

---

## 🏗️ 시스템 아키텍처

<img width="1354" height="842" alt="image" src="https://github.com/user-attachments/assets/6632d4b0-fa04-46e3-af70-ff329823371f" />


---

## 💡 핵심 기술

### 1. MCP (Model Context Protocol) 아키텍처

**기존 방식의 한계**

```java
// Intent 분류 모델 방식
switch(intent) {
    case "SEARCH_SONG": ...
    case "START_GAME": ...
    // 100개 이상의 케이스
    // 새 기능 추가마다 수정 필요
}
```

**MCP 방식**

```json
// GPT가 Tool 자동 선택
{
  "tool_calls": [{
    "name": "start_game_with_song",
    "arguments": {"artist": "트와이스"}
  }]
}
→ McpToolService.executeTool()
```

**장점**

- ✅ **확장성**: Tool 추가만으로 기능 확장
- ✅ **유연성**: 복합 명령 자동 처리
- ✅ **유지보수성**: Intent 분류 로직을 GPT에게 위임

---

### 2. SHA256 해시 기반 TTS 캐싱

**문제**: 같은 응답 "노래를 틀어드릴게요" → 매번 OpenAI TTS API 호출 (1.74초)

**해결**:

```
textHash = sha256("노래를 틀어드릴게요")  # 64자 고정
                    ↓
MongoDB { textHash: "2e7d3c...", audioData: <Binary> }
                    ↓
        캐시 HIT → 0.05초 | 캐시 MISS → 1.74초 (첫 요청만)
```

| 성과 | 수치 |
|---|---|
| API 호출 절감 | **92%** |
| 응답 속도 단축 | **88.5%** |
| 캐시 효율 | 상위 10개 응답이 전체의 78% 차지 |

---

### 3. GCN+CNN 하이브리드 모델

**왜 GCN?**

신체 랜드마크 = 그래프 구조

- **노드**: 어깨, 팔꿈치, 손목 등
- **엣지**: POSE_CONNECTIONS (연결 관계)

| 구성요소 | 역할 |
|---|---|
| **GCN** | 공간 관계 학습 |
| **CNN** | 시간적 패턴 학습 |

**클래스 불균형 해결**

```python
# Inverse Frequency Weighting
class_weights[CLAP] = 총샘플수 / (클래스수 × CLAP개수)
                    = 3670 / (7 × 1117) = 0.47
class_weights[TILT] = 3670 / (7 × 366) = 1.43

CrossEntropyLoss(weight=class_weights)
```

---

## 📈 성과

### 정량적 성과

| 지표 | 수치 |
|:---|:---:|
| 유저 테스트 만족도 | **4.6 / 5.0** |
| 재사용 의향 | **87.5%** |
| 음성 인식률 | **95%** |
| GPT 성공률 | **98.39%** *(+45.45%p)* |
| AI 동작 인식 정확도 | **90%** |
| TTS API 호출 절감 | **92%** |
| TTS 응답 속도 단축 | **88.5%** *(1.74초 → 0.20초)* |
| 전체 응답 시간 | **평균 2초** |
| 학습 데이터 | **3,670개** *(forTrain 대비 7배)* |

### 정성적 성과

**복지관 어르신 피드백**

| Before (초기 프로토타입) | After (최적화 후) |
|:---:|:---:|
| ❌ "말 안 들어서 답답해" | ✅ "이제는 잘 알아듣네!" |
| ❌ "느려서 짜증나" | ✅ "말하자마자 바로 나와서 좋아" |
| ❌ "젊은 사람들이나 쓰는 거 아니야?" | ✅ "우리 같은 노인도 쓸 만해" |

---

## 🚀 시작하기

### 사전 요구사항

- Docker 24.0+
- Docker Compose 2.20+
- Node.js 18+
- Python 3.11+
- Java 17+

### 환경 변수 설정

```bash
# .env.example을 복사하여 .env 파일 생성
cp .env.example .env

# 필수 환경 변수 설정
OPENAI_API_KEY=your_openai_api_key
AWS_ACCESS_KEY=your_aws_access_key
AWS_SECRET_KEY=your_aws_secret_key
MYSQL_ROOT_PASSWORD=your_mysql_password
REDIS_PASSWORD=your_redis_password
```

### Docker Compose로 실행

```bash
# 전체 서비스 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 서비스 중지
docker-compose down
```

### 개별 서비스 실행

**Backend (Spring Boot)**

```bash
cd backend/spring-server
./gradlew bootRun
```

**Motion Server (FastAPI)**

```bash
cd backend/motion-server
pip install -r requirements.txt
python app/main.py
```

**Frontend (React)**

```bash
cd frontend
npm install
npm run dev
```

### 접속

| 서비스 | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Motion API | http://localhost:8000 |
| API Docs | http://localhost:8080/swagger-ui.html |

---

## 📚 문서

- [API Specification](docs/api-specification.md)
- [Architecture](docs/architecture.md)
- [AI Model](docs/ai-model.md)
- [MCP Guide](docs/mcp-guide.md)
- [Emergency System](docs/emergency-system.md)
- [Performance Optimization](docs/performance-optimization.md)

---

## 📄 라이선스

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 감사의 말

- **SSAFY 13기 자율 프로젝트 A103팀**
- 복지관 유저 테스트에 참여해주신 어르신들
- 멘토링 해주신 컨설턴트님들

---

## 🎵 흥부자 - 어르신을 위한 특별한 음악 체조 🎵

Made with ❤️ by **SSAFY 13기 A103팀**
