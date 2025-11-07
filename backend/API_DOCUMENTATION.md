# 흥부자 백엔드 API 문서

Base URL: `http://localhost:8080/api`

---

## 📌 목차
1. [관리자 인증 API](#1-관리자-인증-api)
2. [관리자 관리 API (SUPER_ADMIN 전용)](#2-관리자-관리-api-super_admin-전용)
3. [기기 관리 API](#3-기기-관리-api)
4. [어르신 관리 API](#4-어르신-관리-api)
5. [기기 자동 로그인 API](#5-기기-자동-로그인-api)
6. [음성 명령 API](#6-음성-명령-api)
7. [긴급 신고 API](#7-긴급-신고-api)
8. [WebSocket 실시간 알림](#8-websocket-실시간-알림)
9. [공통 데이터 타입](#9-공통-데이터-타입)

---

## 1. 관리자 인증 API

### 1.1 관리자 회원가입
일반 관리자(ADMIN) 계정을 생성합니다.

**Endpoint:** `POST /api/admins/register`

**Request Body:**
```json
{
  "username": "admin_happy",        // String, 필수, 3~50자
  "password": "password123",        // String, 필수, 최소 6자
  "facilityName": "행복요양원",      // String, 필수, 최대 100자
  "contact": "010-1111-2222",       // String, 선택, 최대 20자
  "email": "happy@example.com"      // String, 선택, 이메일 형식, 최대 100자
}
```

**Response:** `201 Created`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "userId": 2,
  "role": "ROLE_ADMIN"
}
```

---

### 1.2 관리자 로그인

**Endpoint:** `POST /api/admins/login`

**Request Body:**
```json
{
  "username": "superadmin",
  "password": "superadmin123!"
}
```

**Response:** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "role": "ROLE_SUPER_ADMIN"
}
```

---

## 2. 관리자 관리 API (SUPER_ADMIN 전용)

### 2.1 새 관리자 생성

**Endpoint:** `POST /api/admins`

**Headers:**
```
Authorization: Bearer {SUPER_ADMIN_ACCESS_TOKEN}
```

**Request Body:**
```json
{
  "username": "admin_peace",
  "password": "password123",
  "facilityName": "평화요양원",
  "contact": "010-2222-3333",
  "email": "peace@example.com",
  "role": "ADMIN"
}
```

**Response:** `201 Created`

---

### 2.2 전체 관리자 조회

**Endpoint:** `GET /api/admins`

**Headers:**
```
Authorization: Bearer {SUPER_ADMIN_ACCESS_TOKEN}
```

---

## 3. 기기 관리 API

### 3.1 기기 등록

**Endpoint:** `POST /api/admins/devices`

**Headers:**
```
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

**Request Body:**
```json
{
  "serialNumber": "10000000a1b2c3d4",
  "location": "101호"
}
```

---

### 3.2 기기 목록 조회

**Endpoint:** `GET /api/admins/devices`

**Query Parameters:**
- `adminId` (optional): 특정 관리자의 기기 조회 (SUPER_ADMIN만)

---

### 3.3 기기 상세 조회

**Endpoint:** `GET /api/admins/devices/{id}`

---

### 3.4 기기 정보 수정

**Endpoint:** `PUT /api/admins/devices/{id}`

**Request Body:**
```json
{
  "location": "102호",
  "status": "MAINTENANCE"
}
```

---

## 4. 어르신 관리 API

### 4.1 어르신 등록 (+ 기기 매칭)

**Endpoint:** `POST /api/admins/users`

**Headers:**
```
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

**Request Body:**
```json
{
  "name": "김할머니",
  "birthDate": "1950-05-15",
  "gender": "FEMALE",
  "medicalNotes": "고혈압, 당뇨",
  "emergencyContact": "010-9999-8888",
  "deviceId": 1
}
```

---

### 4.2 어르신 목록 조회

**Endpoint:** `GET /api/admins/users`

**Query Parameters:**
- `adminId` (optional): 특정 관리자의 어르신 조회
- `activeOnly` (optional, default: false): 활성 어르신만 조회

---

### 4.3 어르신 상세 조회

**Endpoint:** `GET /api/admins/users/{id}`

---

### 4.4 어르신 정보 수정

**Endpoint:** `PUT /api/admins/users/{id}`

---

### 4.5 어르신 비활성화

**Endpoint:** `PUT /api/admins/users/{id}/deactivate`

---

## 5. 기기 자동 로그인 API

### 5.1 기기 자동 로그인

**Endpoint:** `POST /api/auth/device`

**Request Body:**
```json
{
  "serialNumber": "10000000a1b2c3d4"
}
```

**Response:** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "role": "ROLE_USER"
}
```

---

### 5.2 토큰 갱신

**Endpoint:** `POST /api/auth/refresh`

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## 6. 음성 명령 API

### 6.1 음성 명령 처리

어르신의 음성을 STT로 변환한 텍스트를 받아 의도를 파악하고 적절한 응답을 반환합니다.

**Endpoint:** `POST /api/voice/command`

**Request Body:**
```json
{
  "userId": 1,              // Long, 필수, 어르신 ID
  "text": "태진아 틀어줘"    // String, 필수, STT 변환된 텍스트
}
```

**Validation Rules:**
| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| userId | Long | ✅ | 존재하는 어르신 ID |
| text | String | ✅ | 공백 불가 |

---

### 6.2 음성 명령 응답 형식

#### **6.2.1 노래 재생 명령 (PLAY_SONG)**

**Request:**
```json
{
  "userId": 1,
  "text": "태진아 틀어줘"
}
```

**Response:** `200 OK`
```json
{
  "commandId": 101,                                        // Long, 음성 명령 로그 ID
  "intent": "PLAY_SONG",                                   // String, 의도
  "song": {
    "id": 1,                                               // Long, 곡 ID
    "title": "사랑은 아무나 하나",                          // String, 곡 제목
    "artist": "태진아",                                     // String, 가수명
    "s3Url": "https://s3.amazonaws.com/.../song_1.mp3"    // String, S3 음원 URL
  },
  "message": "태진아의 '사랑은 아무나 하나'를 재생합니다"   // String, TTS 메시지
}
```

**프론트엔드 처리:**
1. `message`를 TTS로 재생
2. `song.s3Url`로 음악 재생

---

#### **6.2.2 재생 제어 명령**

**일시정지 (PAUSE):**
```json
// Request
{
  "userId": 1,
  "text": "잠깐만"
}

// Response
{
  "commandId": 102,
  "intent": "PAUSE"
}
```

**재생 재개 (RESUME):**
```json
// Request
{
  "userId": 1,
  "text": "다시 틀어줘"
}

// Response
{
  "commandId": 103,
  "intent": "RESUME"
}
```

**다음 곡 (NEXT):**
```json
// Request
{
  "userId": 1,
  "text": "다음 곡"
}

// Response
{
  "commandId": 104,
  "intent": "NEXT"
}
```

**정지 (STOP):**
```json
// Request
{
  "userId": 1,
  "text": "그만"
}

// Response
{
  "commandId": 105,
  "intent": "STOP"
}
```

---

#### **6.2.3 인식 실패**

**Response:** `200 OK`
```json
{
  "commandId": 106,
  "intent": "UNKNOWN",
  "message": "죄송합니다. 이해하지 못했습니다"
}
```

---

### 6.3 지원하는 음성 명령 키워드

#### **노래 재생 (PLAY_SONG)**
- 가수명만: "태진아", "태진아 틀어줘", "태진아 노래"
- 곡 제목만: "사랑은 아무나 하나", "사랑은 아무나 하나 틀어줘"
- 가수 + 제목: "태진아 사랑은 아무나 하나", "태진아의 사랑은 아무나 하나 틀어줘"

#### **재생 제어**
- 일시정지: "잠깐", "멈춰", "정지", "일시정지"
- 재생 재개: "다시", "계속", "재생"
- 다음 곡: "다음", "건너뛰기", "스킵"
- 정지: "그만", "종료", "끝"

---

### 6.4 사용 예시

```bash
# 1. 가수명으로 검색
curl -X POST http://localhost:8080/api/voice/command \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "text": "태진아 틀어줘"
  }'

# 2. 일시정지
curl -X POST http://localhost:8080/api/voice/command \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "text": "잠깐만"
  }'
```

---

## 7. 긴급 신고 API

### 7.1 긴급 신고 감지

어르신의 긴급 키워드("살려줘", "도와줘" 등)를 감지하고 2단계 확인 프로세스를 시작합니다.

**Endpoint:** `POST /api/emergency`

**Request Body:**
```json
{
  "userId": 1,              // Long, 필수, 어르신 ID
  "triggerWord": "살려줘"   // String, 필수, 감지된 긴급 키워드
}
```

**Response:** `201 Created`
```json
{
  "reportId": 15,                                    // Long, 신고 ID
  "userId": 1,                                       // Long, 어르신 ID
  "userName": "김할머니",                             // String, 어르신 이름
  "triggerWord": "살려줘",                           // String, 긴급 키워드
  "isConfirmed": false,                              // Boolean, 확정 여부
  "status": "PENDING",                               // String, 상태
  "reportedAt": "2025-10-30T14:30:00",               // LocalDateTime, 신고 시각
  "message": "괜찮으세요? 정말 신고가 필요하신가요?"   // String, TTS 메시지
}
```

**프론트엔드 처리:**
1. `message`를 TTS로 재생
2. 10초 타이머 시작
3. STT로 어르신 응답 대기

---

### 7.2 신고 취소 (어르신 응답)

10초 이내에 어르신이 "괜찮아요" 등으로 응답한 경우 신고를 취소합니다.

**Endpoint:** `PUT /api/emergency/{id}/cancel`

**Path Parameters:**
- `id`: 신고 ID

**Response:** `204 No Content`

**프론트엔드 처리:**
- TTS: "다행입니다. 언제든 불편하시면 말씀해주세요"

---

### 7.3 신고 확정 (10초 무응답)

10초 동안 어르신 응답이 없으면 신고를 확정하고 관리자에게 WebSocket 알림을 전송합니다.

**Endpoint:** `PUT /api/emergency/{id}/confirm`

**Path Parameters:**
- `id`: 신고 ID

**Response:** `200 OK`
```json
{
  "reportId": 15,
  "userId": 1,
  "userName": "김할머니",
  "triggerWord": "살려줘",
  "isConfirmed": true,
  "status": "PENDING",
  "reportedAt": "2025-10-30T14:30:00",
  "message": "관리자에게 알림이 전송되었습니다"
}
```

**자동 처리:**
- WebSocket으로 해당 어르신의 관리자에게 실시간 알림 전송

---

### 7.4 신고 목록 조회 (관리자)

**Endpoint:** `GET /api/emergency/admins/reports`

**Headers:**
```
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

**Response:** `200 OK`
```json
[
  {
    "reportId": 15,
    "userId": 1,
    "userName": "김할머니",
    "triggerWord": "살려줘",
    "isConfirmed": true,
    "status": "PENDING",
    "reportedAt": "2025-10-30T14:30:00",
    "message": null
  }
]
```

---

### 7.5 신고 처리 (관리자)

**Endpoint:** `PUT /api/emergency/admins/reports/{id}`

**Headers:**
```
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

**Query Parameters:**
- `notes`: 관리자 메모 (String)

**Request Example:**
```
PUT /api/emergency/admins/reports/15?notes=확인%20완료%2C%20현장%20출동
```

**Response:** `204 No Content`

---

### 7.6 긴급 신고 플로우

```
1. [프론트] STT → "살려줘"
2. [프론트 → 백엔드] POST /api/emergency
3. [백엔드 → 프론트] "괜찮으세요?" 메시지 + reportId
4. [프론트] TTS + 10초 타이머 시작

5-A. 어르신 "괜찮아요" 응답
   → PUT /api/emergency/{id}/cancel
   → TTS: "다행입니다"

5-B. 10초 무응답
   → PUT /api/emergency/{id}/confirm
   → WebSocket으로 관리자에게 알림
   → TTS: "관리자에게 알림이 전송되었습니다"
```

---

## 8. WebSocket 실시간 알림

### 8.1 연결 설정

**WebSocket Endpoint:** `ws://localhost:8080/ws`

**JavaScript 예시:**
```javascript
const socket = new SockJS('http://localhost:8080/api/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
  console.log('WebSocket Connected');
});
```

---

### 8.2 채널 구독

#### **긴급 신고 알림 구독 (관리자)**

**Channel:** `/topic/admin/{adminId}/emergency`

```javascript
stompClient.subscribe('/topic/admin/2/emergency', (message) => {
  const alert = JSON.parse(message.body);
  console.log('긴급 신고 발생!', alert);

  // 알림 표시
  showEmergencyAlert(alert);
});
```

**메시지 형식:**
```json
{
  "type": "EMERGENCY_REPORT",                  // String, 메시지 타입
  "reportId": 15,                              // Long, 신고 ID
  "userId": 1,                                 // Long, 어르신 ID
  "userName": "김할머니",                       // String, 어르신 이름
  "triggerWord": "살려줘",                     // String, 긴급 키워드
  "reportedAt": "2025-10-30T14:30:00",         // LocalDateTime, 신고 시각
  "priority": "CRITICAL"                       // String, 우선순위
}
```

---

### 8.3 프론트엔드 처리 예시

```javascript
// 긴급 알림 수신 시
function showEmergencyAlert(alert) {
  // 1. 빨간색 배지 표시
  updateBadgeCount('+1');

  // 2. 사운드 재생
  playAlertSound();

  // 3. 팝업 표시
  showModal({
    title: '🚨 긴급 신고',
    message: `${alert.userName}님이 "${alert.triggerWord}"를 외쳤습니다`,
    time: alert.reportedAt,
    actions: ['확인하기', '닫기']
  });

  // 4. 해당 어르신 카드 하이라이트
  highlightUserCard(alert.userId, 'red');
}
```

---

## 9. 공통 데이터 타입

### 9.1 Enum 타입

#### **AdminRole**
```java
enum AdminRole {
  SUPER_ADMIN,  // 최고 관리자
  ADMIN         // 일반 관리자
}
```

#### **DeviceStatus**
```java
enum DeviceStatus {
  REGISTERED,   // 등록됨, 어르신 미매칭
  ACTIVE,       // 어르신과 매칭되어 사용 중
  MAINTENANCE,  // 수리 중
  INACTIVE      // 사용 중지
}
```

#### **Gender**
```java
enum Gender {
  MALE,    // 남성
  FEMALE   // 여성
}
```

#### **ReportStatus**
```java
enum ReportStatus {
  PENDING,       // 대기 중
  RESOLVED,      // 처리 완료
  FALSE_ALARM    // 오탐
}
```

#### **VoiceIntent**
```
PLAY_SONG   // 노래 재생
PAUSE       // 일시정지
RESUME      // 재생 재개
NEXT        // 다음 곡
STOP        // 정지
UNKNOWN     // 인식 실패
```

---

### 9.2 날짜/시간 형식

| Type | Format | Example |
|------|--------|---------|
| LocalDate | ISO-8601 | `"2025-10-30"` |
| LocalDateTime | ISO-8601 | `"2025-10-30T12:30:00"` |

---

## 🔒 권한 및 접근 제어

### **Public 엔드포인트 (인증 불필요)**
- `/api/admins/register`
- `/api/admins/login`
- `/api/auth/device`
- `/api/auth/refresh`
- `/api/voice/command`
- `/api/emergency`
- `/api/emergency/{id}/cancel`
- `/api/emergency/{id}/confirm`

### **SUPER_ADMIN 전용**
- `POST /api/admins`
- `GET /api/admins`

### **ADMIN & SUPER_ADMIN**
- `/api/admins/devices/**`
- `/api/admins/users/**`
- `/api/emergency/admins/**`

---

## 🎯 전체 플로우 예시

### **시나리오: 어르신이 노래 듣다가 긴급 상황 발생**

```
1. [프론트] STT → "태진아 틀어줘"
2. POST /api/voice/command → 노래 정보 받음
3. [프론트] 음악 재생 시작

4. [프론트] STT → "살려줘!"
5. POST /api/emergency → reportId 받음
6. [프론트] TTS: "괜찮으세요?" + 10초 타이머

7. 10초 무응답
8. PUT /api/emergency/{id}/confirm
9. [백엔드] WebSocket → 관리자에게 알림

10. [관리자 웹] 빨간색 배지 + 사운드 + 팝업
11. [관리자] 확인 후 현장 출동
12. PUT /api/emergency/admins/reports/{id}?notes=확인완료
```

---

## 📝 cURL 예시

```bash
# 음성 명령
curl -X POST http://localhost:8080/api/voice/command \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"text":"태진아 틀어줘"}'

# 긴급 신고
curl -X POST http://localhost:8080/api/emergency \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"triggerWord":"살려줘"}'

# 신고 확정
curl -X PUT http://localhost:8080/api/emergency/15/confirm

# 신고 취소
curl -X PUT http://localhost:8080/api/emergency/15/cancel
```

---

**문서 버전:** 2.0
**최종 업데이트:** 2025-10-30
**작성자:** 흥부자 개발팀
