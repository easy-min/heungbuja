# 🔌 MCP (Model Context Protocol) 구현 가이드

## 📋 개요

기존 Intent 기반 방식을 MCP 방식으로 개선한 **대체 구현체**입니다.

**핵심 차이점:**
- ❌ **기존**: GPT가 Intent만 분류 → Java switch문으로 분기
- ✅ **MCP**: GPT가 직접 Tool 선택 → Tool 자동 실행

**기존 코드는 전혀 수정하지 않았습니다!**
새로운 구현체를 만들어서 @Primary로 교체 가능하도록 구현했습니다.

---

## 🏗️ 아키텍처

### 파일 구조

```
backend/spring-server/src/main/java/com/heungbuja/
├── command/
│   ├── service/
│   │   ├── CommandService.java (인터페이스)
│   │   └── impl/
│   │       ├── CommandServiceImpl.java        ← 기존 (Intent + switch)
│   │       └── McpCommandServiceImpl.java     ← 신규 (MCP, @Primary)
│   └── mcp/  ← 새로 추가된 패키지
│       ├── McpToolService.java                ← Tool 실행 서비스
│       └── dto/
│           ├── McpToolDefinition.java         ← Tool 메타데이터
│           ├── McpToolCall.java               ← Tool 호출 정보
│           └── McpToolResult.java             ← Tool 실행 결과
```

### 처리 흐름

```
1. 사용자 음성 "태진아 노래 틀어줘"
   ↓
2. STT (Whisper) → "태진아 노래 틀어줘"
   ↓
3. McpCommandServiceImpl.processTextCommand()
   ├─ Redis에서 대화 컨텍스트 조회
   ├─ GPT에게 Tool 선택 요청 (JSON 응답)
   │  {
   │    "tool_calls": [
   │      {
   │        "name": "search_song",
   │        "arguments": {
   │          "userId": 1,
   │          "artist": "태진아"
   │        }
   │      }
   │    ]
   │  }
   ├─ McpToolService.executeTool("search_song")
   │  → songService.searchByArtist("태진아")
   │  → conversationContextService.setCurrentSong(...)
   │  → SongInfoDto 반환
   ├─ GPT에게 Tool 결과 전달 → 자연스러운 응답 생성
   │  "태진아의 '사랑은 아무나 하나'를 재생할게요"
   └─ TTS 음성 생성 및 응답
```

---

## 🛠️ 구현된 MCP Tools (7개)

### 1. `search_song`
**설명:** 가수명, 제목, 연대, 장르, 분위기로 노래 검색

**파라미터:**
- `userId` (필수): 사용자 ID
- `artist`: 가수명
- `title`: 곡명
- `era`: 연대 (1980s, 1990s 등)
- `genre`: 장르 (발라드, 댄스, 트로트 등)
- `mood`: 분위기 (슬픈, 경쾌한 등)
- `excludeSongId`: 제외할 곡 ID ("이거 말고 다른 거")

**예시:**
```json
{
  "name": "search_song",
  "arguments": {
    "userId": 1,
    "artist": "태진아",
    "era": "1990s",
    "genre": "발라드"
  }
}
```

### 2. `control_playback`
**설명:** 재생 제어 (일시정지, 재생, 다음곡, 정지)

**파라미터:**
- `userId` (필수): 사용자 ID
- `action` (필수): PAUSE, RESUME, NEXT, STOP

### 3. `add_to_queue`
**설명:** 대기열에 곡 추가

**파라미터:**
- `userId` (필수): 사용자 ID
- `artist` (필수): 가수명
- `count`: 추가할 곡 개수 (기본값: 1)

### 4. `get_current_context`
**설명:** 현재 재생 상태, 대기열 정보 조회

**파라미터:**
- `userId` (필수): 사용자 ID

### 5. `handle_emergency`
**설명:** 응급 상황 감지 및 신고

**파라미터:**
- `userId` (필수): 사용자 ID
- `keyword` (필수): 응급 키워드
- `fullText` (필수): 전체 발화 텍스트

### 6. `change_mode`
**설명:** 모드 변경 (홈, 감상, 체조)

**파라미터:**
- `userId` (필수): 사용자 ID
- `mode` (필수): HOME, LISTENING, EXERCISE

### 7. `start_game` ⭐ 신규 추가
**설명:** 게임(체조)을 시작합니다. 노래에 맞춰 동작을 따라하는 3-5분 게임

**파라미터:**
- `userId` (필수): 사용자 ID
- `songId` (선택): 게임에 사용할 노래 ID (안무 정보가 있는 노래만)

**주의:**
- ✅ 이 Tool은 게임 **시작만** 처리하고 즉시 응답합니다 (1-2초)
- ✅ 게임 진행(3-5분)은 프론트엔드가 `/game/frame`으로 별도 처리
- ✅ MCP는 게임이 끝날 때까지 대기하지 않습니다

**응답에 포함되는 데이터:**
```json
{
  "sessionId": "uuid-1234",
  "songId": 42,
  "songTitle": "허공",
  "songArtist": "조용필",
  "audioUrl": "https://s3.../song.mp3",
  "beatInfo": { ... },
  "choreographyInfo": { ... },
  "lyricsInfo": { ... }
}
```

---

## 🔄 기존 구현체와 전환 방법

### MCP 방식 사용 (현재 기본값)

`McpCommandServiceImpl`에 **@Primary** 애노테이션이 있어서 자동으로 사용됩니다.

```java
@Service
@Primary  // ← 이것 때문에 McpCommandServiceImpl이 기본으로 사용됨
public class McpCommandServiceImpl implements CommandService {
    // ...
}
```

### 기존 Intent 방식으로 되돌리기

`McpCommandServiceImpl.java`에서 **@Primary 제거**:

```java
@Service
// @Primary  ← 이 줄 주석 처리 또는 삭제
public class McpCommandServiceImpl implements CommandService {
    // ...
}
```

그러면 **CommandServiceImpl** (기존 구현체)가 자동으로 사용됩니다.

---

## 🎯 화면 전환 (ScreenTransition)

MCP는 음성 명령에 따라 자동으로 화면 전환 정보를 응답에 포함합니다.

### CommandResponse 구조:

```json
{
  "success": true,
  "intent": "UNKNOWN",
  "responseText": "조용필의 '허공'으로 게임을 시작할게요",
  "ttsAudioUrl": "/commands/tts/abc-123",
  "screenTransition": {  // ⭐ 화면 전환 정보
    "targetScreen": "/game",
    "action": "START_GAME",
    "data": {
      "sessionId": "uuid-1234",
      "audioUrl": "https://...",
      "beatInfo": { ... }
    }
  }
}
```

### 화면 전환 매핑:

| Tool | targetScreen | action | 비고 |
|------|-------------|--------|------|
| `search_song` | `/listening` | `PLAY_SONG` | 노래 재생 |
| `start_game` | `/game` | `START_GAME` | 게임 시작 |
| `change_mode(HOME)` | `/home` | `GO_HOME` | 홈으로 |

---

## 🧪 테스트 방법

### 1. 텍스트 명령 테스트 - 노래 재생

```bash
curl -X POST http://localhost:8080/api/commands/text \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "text": "태진아 노래 틀어줘"
  }'
```

**기대 결과:**
```json
{
  "success": true,
  "intent": "UNKNOWN",
  "responseText": "태진아의 '사랑은 아무나 하나'를 재생할게요",
  "ttsAudioUrl": "/commands/tts/abc-123",
  "songInfo": {
    "songId": 42,
    "title": "사랑은 아무나 하나",
    "artist": "태진아",
    "audioUrl": "https://s3.../song.mp3",
    "mode": "LISTENING"
  },
  "screenTransition": {
    "targetScreen": "/listening",
    "action": "PLAY_SONG",
    "data": {
      "songId": 42,
      "autoPlay": true
    }
  }
}
```

### 2. 텍스트 명령 테스트 - 게임 시작 ⭐

```bash
curl -X POST http://localhost:8080/api/commands/text \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "text": "게임 할래"
  }'
```

**기대 결과:**
```json
{
  "success": true,
  "intent": "UNKNOWN",
  "responseText": "조용필의 '허공'으로 게임을 시작할게요",
  "ttsAudioUrl": "/commands/tts/def-456",
  "screenTransition": {
    "targetScreen": "/game",
    "action": "START_GAME",
    "data": {
      "sessionId": "uuid-1234-5678",
      "songId": 15,
      "songTitle": "허공",
      "songArtist": "조용필",
      "audioUrl": "https://s3.../song.mp3",
      "beatInfo": { "bpm": 120, ... },
      "choreographyInfo": { "moves": [...] },
      "lyricsInfo": { "lyrics": [...] }
    }
  }
}
```

**주의:**
- ✅ 응답은 **즉시** 반환됩니다 (1-2초)
- ✅ 프론트엔드는 `screenTransition.data`를 사용해 `/game` 화면으로 이동
- ✅ 게임 진행(3-5분)은 `/game/frame`으로 별도 처리

### 3. 복잡한 요청 테스트

```bash
curl -X POST http://localhost:8080/api/commands/text \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "text": "태진아 노래 틀어주고 일시정지해줘"
  }'
```

**MCP 방식은 2개의 Tool을 순차 실행합니다:**
1. `search_song(artist="태진아")`
2. `control_playback(action="PAUSE")`

### 4. 로그 확인

애플리케이션 로그에서 MCP 동작 확인:

**노래 재생:**
```
[MCP] 명령 처리 시작: userId=1, text='태진아 노래 틀어줘'
[MCP] GPT에게 Tool 선택 요청
[MCP] GPT Tool 선택 응답: {"tool_calls":[{"name":"search_song","arguments":{"userId":1,"artist":"태진아"}}]}
[MCP] Tool 호출 파싱 완료: name=search_song, args={userId=1, artist=태진아}
MCP Tool 실행: name=search_song, args={userId=1, artist=태진아}
```

**게임 시작:**
```
[MCP] 명령 처리 시작: userId=1, text='게임 할래'
[MCP] GPT에게 Tool 선택 요청
[MCP] GPT Tool 선택 응답: {"tool_calls":[{"name":"start_game","arguments":{"userId":1}}]}
[MCP] Tool 호출 파싱 완료: name=start_game, args={userId=1}
MCP Tool 실행: name=start_game, args={userId=1}
새로운 게임 세션 시작: userId=1, sessionId=uuid-1234
게임 시작 완료: userId=1, sessionId=uuid-1234, songId=15
```

---

## 📊 기존 방식 vs MCP 방식 비교

### 예시 1: "태진아 노래 틀어줘"

| 구현체 | 처리 방식 |
|--------|----------|
| **CommandServiceImpl** (기존) | GPT: `{"intent": "SELECT_BY_ARTIST", "entities": {"artist": "태진아"}}` <br> Java: `switch (intent) { case SELECT_BY_ARTIST → handleSearchByArtist() }` |
| **McpCommandServiceImpl** (MCP) | GPT: `{"tool_calls": [{"name": "search_song", "arguments": {"userId": 1, "artist": "태진아"}}]}` <br> Java: `mcpToolService.executeTool("search_song")` |

**결과:** 동일

---

### 예시 2: "태진아 노래 중에 90년대 발라드"

| 구현체 | 처리 방식 | 결과 |
|--------|----------|------|
| **CommandServiceImpl** (기존) | GPT: `{"intent": "SELECT_BY_ARTIST", "entities": {"artist": "태진아"}}` <br> ❌ "90년대", "발라드" 정보 손실됨 | **부정확** |
| **McpCommandServiceImpl** (MCP) | GPT: `{"tool_calls": [{"name": "search_song", "arguments": {"userId": 1, "artist": "태진아", "era": "1990s", "genre": "발라드"}}]}` | **정확** |

---

### 예시 3: "태진아 노래 틀어주고 일시정지"

| 구현체 | 처리 방식 | 결과 |
|--------|----------|------|
| **CommandServiceImpl** (기존) | GPT: `{"intent": "SELECT_BY_ARTIST", ...}` <br> ❌ "일시정지" 무시됨 (Intent 1개만 처리) | **1개만 처리** |
| **McpCommandServiceImpl** (MCP) | GPT: `{"tool_calls": [{"name": "search_song", ...}, {"name": "control_playback", "arguments": {"action": "PAUSE"}}]}` <br> ✅ 2개 Tool 순차 실행 | **모두 처리** |

---

## 💡 MCP의 장점 요약

### 1. **복잡한 조건 처리**
```
사용자: "태진아 노래 중에 90년대 발라드 틀어줘"
MCP: era="1990s", genre="발라드" 파라미터 활용 ✅
기존: 파라미터 손실 ❌
```

### 2. **다중 작업 처리**
```
사용자: "태진아 노래 틀어주고 조용필 3곡 대기열에 추가해줘"
MCP: search_song() + add_to_queue() 순차 실행 ✅
기존: 첫 번째 Intent만 처리 ❌
```

### 3. **문맥 기반 대화**
```
사용자: "이거 말고 다른 거"
MCP: excludeSongId 파라미터 활용 ✅
기존: "UNKNOWN" 처리 ❌
```

### 4. **코드 간소화**
```
기존: switch문 15개 case + handleXXX 메서드 15개
MCP: @McpTool 메서드 6개
```

---

## 🎮 게임 모드 전환 플로우

### 시나리오: "지니야, 게임 할래"

```
시간    | MCP                           | 프론트엔드
--------|-------------------------------|---------------------------
0초     | "게임 할래" 요청 받음          |
0.5초   | GPT에게 Tool 선택 요청         |
1초     | start_game Tool 실행          |
        |  → gameService.startGame()   |
        |  → Redis 세션 생성           |
        |  → 응답 데이터 준비           |
1.5초   | GPT에게 최종 응답 생성         |
2초     | TTS 생성                      |
2.5초   | ✅ 응답 반환 (MCP 종료!) ────→ | 응답 받음
        |                               | TTS 재생
        |                               | /game 화면으로 이동
        |                               |
3초     |                               | 노래 재생 시작
        |                               | 카메라 시작
        |                               | ┌─────────────────┐
4-180초 |                               | │ 프레임 캡처     │
        |                               | │ POST /game/frame│ ← 일반 REST API
        |                               | │ (MCP 아님!)     │
        |                               | └─────────────────┘
181초   |                               | POST /game/end
        |                               | 결과 화면 표시
```

**핵심:**
- ✅ MCP는 **2.5초에 응답하고 끝**
- ✅ 게임 진행(3-5분)은 **별도 REST API** (`/game/frame`)
- ✅ HTTP 타임아웃 걱정 없음

---

## 🚧 향후 개선 사항

### 1. ~~실제 GPT Function Calling API 지원~~
현재는 GPT에게 JSON 형식으로 Tool 선택을 요청하는 방식입니다.
OpenAI의 Function Calling API를 지원하면 더 정확해집니다.

### 2. Era, Genre, Mood 기반 검색 구현
`McpToolService.searchSong()`에서 현재는 artist/title만 검색합니다.
향후 `songService`에 era, genre, mood 파라미터를 추가하면 됩니다.

### 3. excludeSongId 처리
"이거 말고 다른 거" 요청 시 동일한 조건으로 다른 노래를 검색하는 로직 추가

### 4. 대기열 다음 곡 재생
`handlePlayNextInQueue()`에서 Redis 대기열을 활용한 자동 재생 구현

### 5. 게임용 노래 자동 선택
`start_game` Tool에서 songId가 없을 때 안무 정보가 있는 노래만 자동 선택

---

## 📝 주요 클래스 설명

### McpCommandServiceImpl
- **역할**: CommandService 인터페이스 구현, MCP 방식으로 명령 처리
- **주요 메서드**:
  - `processTextCommand()`: 음성 명령 처리 메인 메서드
  - `parseToolCallsFromGptResponse()`: GPT 응답에서 Tool 호출 정보 파싱
  - `executeTools()`: Tool 실행
  - `generateFinalResponse()`: Tool 결과 기반 최종 응답 생성

### McpToolService
- **역할**: MCP Tool 실제 구현체
- **주요 메서드**:
  - `executeTool()`: Tool 호출 실행 (switch문으로 분기)
  - `searchSong()`: 노래 검색 Tool
  - `controlPlayback()`: 재생 제어 Tool
  - `addToQueue()`: 대기열 추가 Tool
  - `getCurrentContext()`: 현재 상태 조회 Tool
  - `handleEmergency()`: 응급 상황 Tool
  - `changeMode()`: 모드 변경 Tool
  - `startGame()`: 게임 시작 Tool ⭐ 신규 추가

### McpToolDefinition
- **역할**: Tool 메타데이터 정의
- **주요 메서드**:
  - `searchSongTool()`: search_song Tool 정의
  - `controlPlaybackTool()`: control_playback Tool 정의
  - `getAllTools()`: 모든 Tool 정의 반환

---

## ✅ 체크리스트

배포 전 확인 사항:

- [x] McpCommandServiceImpl에 @Primary 있는지 확인 (MCP 사용 시)
- [ ] GPT API 키 설정 확인 (`gpt.api.key`)
- [ ] Redis 연결 확인 (대화 컨텍스트 저장)
- [ ] 텍스트 명령 테스트 (`/api/commands/text`)
- [ ] 음성 파일 업로드 테스트 (`/api/commands/process`)
- [ ] 로그에서 `[MCP]` 태그 확인

---

## 🔄 버전 이력

### v1.1.0 (2025-11-05) - 게임 모드 추가
- ✅ `start_game` Tool 추가 (게임 시작)
- ✅ `CommandResponse`에 `ScreenTransition` 필드 추가
- ✅ 화면 전환 자동 처리 (LISTENING, EXERCISE 모드)
- ✅ 게임 데이터 응답에 포함 (sessionId, beatInfo, choreographyInfo 등)
- ✅ 게임 로직은 건드리지 않음 (MCP 쪽만 수정)

### v1.0.0 (2025-11-05)
- ✅ MCP 방식 초기 구현 완료
- ✅ 6개 Tool 구현 (search_song, control_playback, add_to_queue, get_current_context, handle_emergency, change_mode)
- ✅ GPT JSON 응답 파싱 방식 구현
- ✅ 기존 코드 전혀 수정하지 않음 (새 구현체로 분리)
- ✅ @Primary로 교체 가능하도록 구현

---

## 👥 구현자

**백엔드 개발자**: MCP 구현 담당
