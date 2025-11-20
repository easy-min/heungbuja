-- ===================================================================
-- Heungbuja 프로젝트 MySQL 초기 스키마 덤프
-- 생성일: 2025-11-20
-- 버전: 1.0
-- 환경: MySQL 8.0+
-- ===================================================================

-- 데이터베이스 생성 (없으면 생성)
CREATE DATABASE IF NOT EXISTS heungbuja_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE heungbuja_db;

-- ===================================================================
-- 1. 관리자 / 기기 / 사용자
-- ===================================================================

-- 관리자(admins)
CREATE TABLE IF NOT EXISTS admins (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(255) NOT NULL,
  facility_name VARCHAR(100),
  contact VARCHAR(20),
  email VARCHAR(100),
  role VARCHAR(20) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admins_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 기기(devices)
CREATE TABLE IF NOT EXISTS devices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  serial_number VARCHAR(50) NOT NULL,
  location VARCHAR(100),
  status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
  last_active_at DATETIME NULL,
  admin_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_devices_serial_number (serial_number),
  KEY idx_devices_admin_id (admin_id),
  CONSTRAINT fk_devices_admin
    FOREIGN KEY (admin_id) REFERENCES admins(id)
      ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 사용자(users)
CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  birth_date DATE,
  gender VARCHAR(10),
  medical_notes TEXT,
  emergency_contact VARCHAR(20),
  device_id BIGINT UNIQUE,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  admin_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_users_admin_id (admin_id),
  CONSTRAINT fk_users_admin
    FOREIGN KEY (admin_id) REFERENCES admins(id)
      ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_users_device
    FOREIGN KEY (device_id) REFERENCES devices(id)
      ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 리프레시 토큰(refresh_tokens)
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  token VARCHAR(500) NOT NULL,
  user_id BIGINT NULL,
  device_id BIGINT NULL,
  admin_id BIGINT NULL,
  expires_at DATETIME NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_tokens_token (token),
  KEY idx_refresh_tokens_user_id (user_id),
  KEY idx_refresh_tokens_device_id (device_id),
  KEY idx_refresh_tokens_admin_id (admin_id),
  KEY idx_refresh_tokens_expires_at (expires_at),
  CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_refresh_tokens_device
    FOREIGN KEY (device_id) REFERENCES devices(id)
      ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_refresh_tokens_admin
    FOREIGN KEY (admin_id) REFERENCES admins(id)
      ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================================================================
-- 2. 미디어 / 음악 / 감상 이력
-- ===================================================================

-- S3 미디어 정보(media)
CREATE TABLE IF NOT EXISTS media (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255),
  type VARCHAR(50),
  s3Key VARCHAR(255),
  bucket VARCHAR(100),
  uploaderId BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 곡 정보(songs)
CREATE TABLE IF NOT EXISTS songs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  artist VARCHAR(100) NOT NULL,
  media_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_songs_media_id (media_id),
  KEY idx_songs_title (title),
  CONSTRAINT fk_songs_media
    FOREIGN KEY (media_id) REFERENCES media(id)
      ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 청취 이력(listening_histories)
CREATE TABLE IF NOT EXISTS listening_histories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  mode VARCHAR(20) NOT NULL,
  played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_listening_histories_user_id (user_id),
  KEY idx_listening_histories_song_id (song_id),
  KEY idx_listening_histories_user_song (user_id, song_id),
  CONSTRAINT fk_listening_histories_user
    FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_listening_histories_song
    FOREIGN KEY (song_id) REFERENCES songs(id)
      ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================================================================
-- 3. 음성 명령 / 활동 로그 / 성능 로그
-- ===================================================================

-- 음성 명령 로그(voice_commands)
CREATE TABLE IF NOT EXISTS voice_commands (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  raw_text TEXT NOT NULL,
  intent VARCHAR(50) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_voice_commands_user_id (user_id),
  KEY idx_voice_commands_intent (intent),
  CONSTRAINT fk_voice_commands_user
    FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 사용자 활동 로그(user_activity_logs)
CREATE TABLE IF NOT EXISTS user_activity_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  activity_type VARCHAR(30) NOT NULL,
  activity_summary VARCHAR(100) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_activity_logs_user_created (user_id, created_at),
  KEY idx_user_activity_logs_activity_type (activity_type),
  KEY idx_user_activity_logs_created_at (created_at),
  CONSTRAINT fk_user_activity_logs_user
    FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 성능 측정 로그(performance_logs)
CREATE TABLE IF NOT EXISTS performance_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  component VARCHAR(100) NOT NULL,
  methodName VARCHAR(200) NOT NULL,
  executionTimeMs BIGINT NOT NULL,
  requestId VARCHAR(100),
  userId BIGINT,
  success TINYINT(1) NOT NULL,
  errorMessage VARCHAR(500),
  metadata VARCHAR(1000),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_performance_logs_component (component),
  KEY idx_performance_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================================================================
-- 4. 응급 신고 / 게임 결과
-- ===================================================================

-- 응급 신고(emergency_reports)
CREATE TABLE IF NOT EXISTS emergency_reports (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  trigger_word VARCHAR(100) NOT NULL,
  full_text TEXT,
  is_confirmed TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL,
  reported_at DATETIME NOT NULL,
  handled_by BIGINT,
  admin_notes TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_emergency_reports_user_id (user_id),
  KEY idx_emergency_reports_status (status),
  CONSTRAINT fk_emergency_reports_user
    FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_emergency_reports_admin
    FOREIGN KEY (handled_by) REFERENCES admins(id)
      ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 동작 마스터(action)
CREATE TABLE IF NOT EXISTS action (
  action_id BIGINT NOT NULL AUTO_INCREMENT,
  actionCode INT NOT NULL,
  name VARCHAR(50) NOT NULL,
  description VARCHAR(255),
  createdAt DATETIME,
  updatedAt DATETIME,
  PRIMARY KEY (action_id),
  UNIQUE KEY uk_action_action_code (actionCode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 게임 결과(game_result)
CREATE TABLE IF NOT EXISTS game_result (
  game_result_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  session_id VARCHAR(255),
  status VARCHAR(50),
  start_time DATETIME,
  end_time DATETIME,
  interrupt_reason VARCHAR(255),
  verse1_avg_score DOUBLE,
  verse2_avg_score DOUBLE,
  final_level INT,
  updatedAt DATETIME,
  PRIMARY KEY (game_result_id),
  UNIQUE KEY uk_game_result_session_id (session_id),
  KEY idx_game_result_user_id (user_id),
  KEY idx_game_result_song_id (song_id),
  CONSTRAINT fk_game_result_user
    FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_game_result_song
    FOREIGN KEY (song_id) REFERENCES songs(id)
      ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 동작별 점수(ScoreByAction)
CREATE TABLE IF NOT EXISTS ScoreByAction (
  id BIGINT NOT NULL AUTO_INCREMENT,
  game_result_id BIGINT NOT NULL,
  actionCode INT NOT NULL,
  averageScore DOUBLE NOT NULL,
  PRIMARY KEY (id),
  KEY idx_score_by_action_game_result_id (game_result_id),
  CONSTRAINT fk_score_by_action_game_result
    FOREIGN KEY (game_result_id) REFERENCES game_result(game_result_id)
      ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ===================================================================
-- 5. TTS 캐시 (Flyway V1과 동일 구조)
-- ===================================================================

CREATE TABLE IF NOT EXISTS tts_cache (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
  text VARCHAR(500) NOT NULL COMMENT '음성으로 변환할 텍스트',
  voice_type VARCHAR(50) NOT NULL DEFAULT 'default' COMMENT '음성 타입(nova, alloy, shimmer 등)',
  audio_data MEDIUMBLOB NOT NULL COMMENT 'MP3 바이너리 데이터(최대 16MB)',
  file_size INT NOT NULL COMMENT '파일 크기 (바이트)',
  hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '캐시 히트 횟수',
  last_used_at TIMESTAMP NULL COMMENT '마지막 사용 시각',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_text_voice_type (text, voice_type),
  KEY idx_created_at (created_at),
  KEY idx_voice_type (voice_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TTS 음성 캐시';

-- ===================================================================
-- 6. 기본 데이터 (필요시 추가)
-- ===================================================================

-- 예시: 기본 관리자 계정 (비밀번호는 실제 운영에서는 반드시 변경 필요)
-- INSERT INTO admins (username, password, facility_name, contact, email, role)
-- VALUES ('admin', '{bcrypt_encoded_password}', '기본 시설', '010-0000-0000', 'admin@example.com', 'ADMIN');
