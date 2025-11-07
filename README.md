# 흥부자

## 프로젝트 개요
흥부자 프로젝트의 백엔드 서버입니다.

## 기술 스택
- **Backend**: Spring Boot 3.5.7
- **Database**: MySQL 8.0.42
- **Language**: Java 17
- **Build Tool**: Gradle
- **Container**: Docker, Docker Compose

## 프로젝트 구조
```
S13P31A103/
├── docker-compose.yml                # Docker Compose 설정 (프로젝트 루트)
├── .env                              # 환경 변수 (로컬, git 제외)
├── .env.example                      # 환경 변수 예시 파일
├── backend/
│   └── spring-server/                # Spring Boot 프로젝트
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/com/heungbuja/
│       │   │   └── resources/
│       │   │       └── application.yml
│       │   └── test/
│       ├── Dockerfile
│       ├── build.gradle
│       └── settings.gradle
└── README.md
```

## 환경 설정

### 1. 환경 변수 파일 생성
프로젝트 루트에 `.env` 파일을 생성하세요.
`.env.example`을 참고하여 필요한 환경 변수를 설정합니다.

```bash
cp .env.example .env
# .env 파일을 편집하여 실제 값 입력
```

`.env` 파일 예시:
```env
# MySQL Database Configuration
MYSQL_ROOT_PASSWORD=root1234
MYSQL_DATABASE=heungbuja_db
MYSQL_USER=heungbuja_user
MYSQL_PASSWORD=heungbuja1234

# Spring Boot Configuration
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
```

### 2. Docker Compose로 서비스 실행
```bash
docker-compose up -d
```

### 3. 서비스 확인
- Spring Boot: http://localhost:8080
- Health Check: http://localhost:8080/health

## 개발 환경 설정

### 로컬 개발 (Docker 없이)
1. MySQL을 로컬에 설치하고 실행
2. `.env` 파일에서 가져온 설정 값으로 `application.yml` 수정
3. Spring Boot 실행

```bash
cd backend/spring-server
./gradlew bootRun
```

## 주요 기능
- Health Check 엔드포인트 (`/health`)
- MySQL 데이터베이스 연결
- JPA를 통한 ORM 지원

## API 엔드포인트
- `GET /health` - 서비스 헬스 체크

## 빌드 및 배포
```bash
# Docker 이미지 빌드
docker-compose build

# 로그 확인
docker-compose logs -f spring
```

## 문제 해결

### Docker 컨테이너가 시작되지 않는 경우
```bash
# 로그 확인
docker-compose logs

# 컨테이너 상태 확인
docker-compose ps

# 컨테이너 재시작
docker-compose restart spring
```
