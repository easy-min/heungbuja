# 🎤 음성 인터페이스 구현 문서

## 📋 개요

어르신을 위한 음성 명령 기반 음악 재생 및 응급 상황 감지 시스템

**중요**: 프론트엔드가 음악 재생을 관리하므로, 백엔드는:
- ✅ 노래 정보만 전달 (audioUrl)
- ✅ 청취 이력만 기록 (추천 시스템용)
- ❌ 재생 상태 관리 없음 (PLAYING, PAUSED 등)

---

## 🏗️ 아키텍처 설계 원칙

### ✨ **느슨한 결합 (Loose Coupling)**
- **인터페이스 기반 설계**로 구현체 교체 가능
- **전략 패턴(Strategy Pattern)** 적용
- **의존성 역전 원칙(DIP)** 준수

### 🔧 구현체 교체 가능한 모듈

#### 1. **STT (Speech-to-Text)**
```java
// 인터페이스
SttService

// 현재 구현체
MockSttServiceImpl (개발용)

// 추후 교체 가능
- WhisperSttServiceImpl (OpenAI Whisper API)
- GoogleSttServiceImpl (Google Cloud STT)
- NaverClovaSttServiceImpl (Naver Clova)
```

#### 2. **TTS (Text-to-Speech)**
```java
// 인터페이스
TtsService

// 현재 구현체
SimpleTtsServiceImpl (로컬 파일 저장)

// 추후 교체 가능
- GoogleTtsServiceImpl (Google Cloud TTS)
- AwsPollyTtsServiceImpl (AWS Polly)
- NaverClovaTtsServiceImpl (Naver Clova)
```

#### 3. **Intent 분석**
```java
// 인터페이스
IntentClassifier

// 현재 구현체
KeywordBasedIntentClassifier (키워드 매칭)

// 추후 교체 가능
- RagBasedIntentClassifier (RAG 기반)
- LlmIntentClassifier (LLM 기반)
- MlIntentClassifier (ML 모델 기반)
```

---

## 📁 파일 구조

```
backend/spring-server/src/main/java/com/heungbuja/

├── voice/                              # 음성 처리 도메인
│   ├── enums/
│   │   └── Intent.java                 # 의도 열거형
│   ├── service/
│   │   ├── SttService.java             # STT 인터페이스
│   │   ├── TtsService.java             # TTS 인터페이스
│   │   └── impl/
│   │       ├── MockSttServiceImpl.java
│   │       └── SimpleTtsServiceImpl.java
│   ├── entity/VoiceCommand.java        # 음성 명령 로그
│   └── repository/VoiceCommandRepository.java

├── command/                            # 명령어 분석 도메인
│   ├── controller/
│   │   └── CommandController.java      # 통합 API
│   ├── service/
│   │   ├── IntentClassifier.java       # 의도 분석 인터페이스
│   │   ├── CommandService.java         # 통합 명령 처리
│   │   ├── ResponseGenerator.java      # 응답 생성
│   │   └── impl/
│   │       └── KeywordBasedIntentClassifier.java
│   └── dto/
│       ├── IntentResult.java
│       ├── CommandRequest.java
│       └── CommandResponse.java

├── music/                              # 음악 도메인 (간소화)
│   ├── entity/
│   │   └── ListeningHistory.java      # 청취 이력 (추천용)
│   ├── repository/
│   │   └── ListeningHistoryRepository.java
│   ├── service/
│   │   └── ListeningHistoryService.java # 이력 기록만
│   ├── enums/
│   │   ├── PlaybackMode.java           # LISTENING, EXERCISE
│   │   └── SearchType.java
│   └── dto/
│       └── SongInfoDto.java            # 노래 정보 (상태 없음)

└── song/                               # 노래 도메인 (기존)
    ├── entity/Song.java
    ├── repository/SongRepository.java
    └── service/SongService.java
```

---

## 🎯 Intent 종류

### 음악 검색
- `SELECT_BY_ARTIST` - 가수명으로 검색
- `SELECT_BY_TITLE` - 제목으로 검색
- `SELECT_BY_ARTIST_TITLE` - 가수+제목으로 검색

### 재생 제어 (프론트가 관리, 백엔드는 TTS 응답만)
- `MUSIC_PAUSE` - 일시정지
- `MUSIC_RESUME` - 재생 재개
- `MUSIC_NEXT` - 다음 곡
- `MUSIC_STOP` - 재생 종료

### 모드 관련 (프론트가 관리, 백엔드는 TTS 응답만)
- `MODE_LISTENING_START` - 감상 모드 시작
- `MODE_EXERCISE_START` - 체조 모드 시작
- `MODE_SWITCH_TO_LISTENING` - 감상 모드로 전환
- `MODE_SWITCH_TO_EXERCISE` - 체조 모드로 전환

### 응급 상황
- `EMERGENCY` - 응급 상황 감지

### 기타
- `UNKNOWN` - 인식 불가

---

## 🔌 API 엔드포인트

### 1. 통합 음성 명령 처리
```http
POST /api/commands/process
Content-Type: multipart/form-data

Parameters:
- userId: Long (사용자 ID)
- audioFile: MultipartFile (음성 파일)

Response:
{
  "success": true,
  "intent": "SELECT_BY_ARTIST",
  "responseText": "태진아의 '사랑은 아무나 하나'를 재생할게요",
  "ttsAudioUrl": "/api/commands/tts/abc123",
  "songInfo": {
    "songId": 42,
    "title": "사랑은 아무나 하나",
    "artist": "태진아",
    "audioUrl": "https://s3.../song.mp3",
    "mode": "LISTENING"
  }
}
```

### 2. 텍스트 명령 처리 (디버깅용)
```http
POST /api/commands/text
Content-Type: application/json

{
  "userId": 1,
  "text": "태진아 노래 틀어줘"
}

Response: (동일)
```

### 3. TTS 음성 파일 다운로드
```http
GET /api/commands/tts/{fileId}

Response: audio/mpeg (MP3 파일)
```

---

## 🔄 처리 흐름

### 시나리오 1: "태진아 노래 틀어줘"

```
1. 클라이언트
   - 웨이크업 워드 "지니야" 감지 (로컬)
   - "네!" 피드백 재생
   - 5초간 명령 녹음

2. 서버: POST /api/commands/process
   ├─ SttService: 음성 → 텍스트 변환
   │  └─ "태진아 노래 틀어줘"
   │
   ├─ IntentClassifier: 의도 분석
   │  └─ Intent.SELECT_BY_ARTIST, { artist: "태진아" }
   │
   ├─ SongService: 노래 검색
   │  └─ Song(id=42, title="사랑은 아무나 하나", artist="태진아")
   │
   ├─ ListeningHistoryService: 청취 이력 기록
   │  └─ ListeningHistory(user, song, mode=LISTENING)
   │
   ├─ ResponseGenerator: 응답 메시지 생성
   │  └─ "태진아의 '사랑은 아무나 하나'를 재생할게요"
   │
   └─ TtsService: TTS 음성 생성
      └─ fileId: "abc123"

3. 클라이언트
   - TTS 음성 재생
   - 노래 재생 (audioUrl)
   - 재생 상태 관리 (프론트가 담당)
```

### 시나리오 2: "일시정지"

```
1. 클라이언트
   - "일시정지" 음성 감지

2. 서버: POST /api/commands/process
   ├─ IntentClassifier: MUSIC_PAUSE 감지
   │
   ├─ ResponseGenerator: 응답 메시지 생성
   │  └─ "일시정지할게요"
   │
   └─ TtsService: TTS 음성 생성
      └─ fileId: "def456"

3. 클라이언트
   - TTS 음성 재생
   - 음악 일시정지 (프론트가 처리)
```

### 시나리오 3: "도와줘!" (응급 상황)

```
1. 클라이언트
   - "도와줘" 음성 감지 (항상 대기)

2. 서버: POST /api/commands/process
   ├─ IntentClassifier: EMERGENCY 감지
   │
   ├─ EmergencyService: 응급 신고 생성
   │  └─ EmergencyReport 저장
   │  └─ WebSocket 알림 전송 (/topic/admin/{adminId}/emergency)
   │
   └─ TtsService: "괜찮으세요? 대답해주세요!" 생성

3. 클라이언트
   - 음악 중지 (프론트가 처리)
   - TTS 긴급 메시지 재생
   - 10초 타이머 시작
```

---

## 🛠️ 기술 스택

| 구성 요소 | 기술 | 비고 |
|----------|------|------|
| STT | OpenAI Whisper (예정) | 현재 Mock 구현 |
| TTS | Google TTS / AWS Polly / Naver Clova (선택 예정) | 현재 로컬 파일 저장 |
| Intent 분석 | 키워드 매칭 | 추후 RAG/LLM으로 교체 가능 |
| 데이터베이스 | Spring Data JPA / MySQL | ListeningHistory, VoiceCommand 저장 |
| 실시간 통신 | WebSocket (STOMP) | 응급 알림 전송 |

---

## 🔐 보안 설정

### SecurityConfig
```java
// Public endpoints (인증 불필요)
.requestMatchers("/commands/**").permitAll()
```

음성 명령은 기기에서 직접 호출하므로 공개 엔드포인트로 설정

---

## 🚀 확장 계획

### Phase 2 (다음 단계)
1. **실제 STT 연동**
   - OpenAI Whisper API 연동
   - 실시간 스트리밍 처리

2. **실제 TTS 연동**
   - 비용 효율적인 서비스 선택
   - 음성 품질 개선

3. **플레이리스트 기능**
   - 다음 곡 자동 재생
   - 큐 관리

### Phase 3 (고급 기능)
1. **RAG 기반 Intent 분석**
   - 자연어 처리 개선
   - 문맥 이해

2. **추천 시스템**
   - 청취 이력 기반 선호 분석
   - 맞춤형 노래 추천

3. **체조 모드 강화**
   - 실시간 격려 멘트
   - AI 동작 피드백

---

## 📊 데이터베이스 스키마

### listening_histories 테이블 (추천용)
```sql
CREATE TABLE listening_histories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    song_id BIGINT NOT NULL,
    mode VARCHAR(20) NOT NULL,        -- LISTENING, EXERCISE
    played_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (song_id) REFERENCES songs(id)
);
```

### voice_commands 테이블 (로그)
```sql
CREATE TABLE voice_commands (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    raw_text TEXT NOT NULL,
    intent VARCHAR(50) NOT NULL,
    created_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

---

## 💡 주요 구현 포인트

### 1. **프론트엔드 중심 재생 관리**
```java
// 백엔드는 노래 정보만 제공
public CommandResponse handleSearchByArtist(...) {
    Song song = songService.searchByArtist(query);

    // 청취 이력만 기록 (추천용)
    listeningHistoryService.recordListening(user, song, mode);

    // 노래 정보 전달 (프론트가 재생)
    return CommandResponse.withSong(..., SongInfoDto.from(song, mode));
}

// 재생 제어는 TTS 응답만
public CommandResponse handleSimpleResponse(Intent intent) {
    String responseText = responseGenerator.generateResponse(intent);
    String ttsUrl = ttsService.synthesize(responseText);

    return CommandResponse.success(intent, responseText, ttsUrl);
    // songInfo 없음 - 프론트가 자체 관리
}
```

### 2. **인터페이스 기반 설계**
```java
// 나중에 다른 구현체로 교체 가능
@Service
public class CommandService {
    private final IntentClassifier intentClassifier;  // 인터페이스
    private final TtsService ttsService;              // 인터페이스
    private final SttService sttService;              // 인터페이스

    // 생성자 주입 (DI)
    public CommandService(IntentClassifier intentClassifier, ...) {
        this.intentClassifier = intentClassifier;
        // ...
    }
}
```

### 3. **전략 패턴으로 Intent 분류기 교체**
```java
// 현재
@Component
public class KeywordBasedIntentClassifier implements IntentClassifier {
    @Override
    public IntentResult classify(String text) {
        // 키워드 매칭
    }
}

// 미래 (RAG)
@Component
@Primary  // 이것만 추가하면 교체 완료!
public class RagBasedIntentClassifier implements IntentClassifier {
    @Override
    public IntentResult classify(String text) {
        // RAG 기반 분석
    }
}
```

---

## 🧪 테스트 방법

### 1. 텍스트 명령 테스트
```bash
curl -X POST http://localhost:8080/api/commands/text \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "text": "태진아 노래 틀어줘"
  }'
```

### 2. 음성 파일 업로드 테스트
```bash
curl -X POST http://localhost:8080/api/commands/process \
  -F "userId=1" \
  -F "audioFile=@voice.mp3"
```

### 3. TTS 파일 다운로드 테스트
```bash
curl http://localhost:8080/api/commands/tts/abc123 \
  --output response.mp3
```

---

## 📝 환경 변수 설정

### application.yml
```yaml
# TTS 파일 저장 경로
tts:
  storage:
    path: ${TTS_STORAGE_PATH:./tts-files}
```

---

## ⚠️ 알려진 제한사항

1. **현재 STT는 Mock 구현**
   - 실제 음성 인식 전까지 "태진아 노래 틀어줘" 고정 반환

2. **현재 TTS는 빈 파일 생성**
   - 실제 음성 합성 전까지 더미 파일 생성

3. **재생 상태는 프론트가 관리**
   - 백엔드는 노래 정보만 제공
   - PAUSE/RESUME/STOP은 TTS 응답만

---

## 🔄 변경 이력

### v1.1.0 (2025-11-03) - 아키텍처 간소화
- ❌ **제거**: PlaybackSession, PlaybackService (상태 관리)
- ✅ **추가**: ListeningHistory (청취 이력만)
- ✅ **변경**: 재생 제어 명령은 TTS 응답만 (프론트가 재생 관리)
- ✅ **이유**: 프론트엔드가 음악 재생을 전담하므로 백엔드 역할 축소

### v1.0.0 (2025-11-02)
- 초기 구현 완료
- 인터페이스 기반 아키텍처 구축
- 키워드 기반 Intent 분류
- 응급 상황 통합

---

## 👥 구현자

**백엔드 개발자**: 음성 인터페이스 담당
