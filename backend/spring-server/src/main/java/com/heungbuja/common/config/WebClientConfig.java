package com.heungbuja.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // application.yml에서 AI 서버 주소를 읽어옴
    @Value("${ai.server.base-url}")
    private String aiServerBaseUrl;

    @Bean
    public WebClient aiWebClient() {
        return WebClient.builder()
                .baseUrl(aiServerBaseUrl)
                .build();
    }
}