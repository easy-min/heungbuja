package com.heungbuja;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;


@EnableAsync
@SpringBootApplication
public class HeungbujaBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(HeungbujaBeApplication.class, args);
	}

}
