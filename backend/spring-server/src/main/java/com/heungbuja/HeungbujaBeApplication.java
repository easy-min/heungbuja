package com.heungbuja;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableAsync;


@EnableAsync
@SpringBootApplication
// --- ▼ (핵심) 각 데이터 모듈의 스캔 범위를 명시적으로 지정 ▼ ---
@EnableJpaRepositories(basePackages = {
		"com.heungbuja.admin.repository", "com.heungbuja.auth.repository",
		"com.heungbuja.device.repository", "com.heungbuja.emergency.repository",
		"com.heungbuja.game.repository.jpa", "com.heungbuja.s3.repository",
		"com.heungbuja.song.repository.jpa", // <-- 수정된 경로
		"com.heungbuja.user.repository", "com.heungbuja.voice.repository"
})
@EnableMongoRepositories(basePackages = {
		"com.heungbuja.song.repository.mongo", // <-- 수정된 경로
		"com.heungbuja.game.repository.mongo"
})
@EnableRedisRepositories(basePackages = {
		"com.heungbuja.context.repository", // <-- Redis Repository가 있는 실제 경로로 수정 필요
		"com.heungbuja.session"
})
public class HeungbujaBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(HeungbujaBeApplication.class, args);
	}

}
