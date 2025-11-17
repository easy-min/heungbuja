package com.heungbuja.song.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * music-server (FastAPI) 클라이언트
 * 오디오 분석 API 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MusicServerClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.music-server.url:http://localhost:8001}")
    private String musicServerUrl;

    /**
     * 오디오 파일과 가사를 music-server로 전송하여 분석
     *
     * @param audioFile 오디오 파일
     * @param lyricsText 가사 텍스트
     * @param title 곡 제목
     * @return 분석 결과 JSON (beats, lyrics 포함)
     */
    public JsonNode analyzeAudio(MultipartFile audioFile, String lyricsText, String title) {
        try {
            // MultipartFile을 FormData로 변환
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // 오디오 파일
            body.add("audio", new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return audioFile.getOriginalFilename();
                }
            });

            // 가사 파일 (텍스트를 ByteArrayResource로 변환)
            body.add("lyrics", new ByteArrayResource(lyricsText.getBytes()) {
                @Override
                public String getFilename() {
                    return "lyrics.txt";
                }
            });

            // 제목
            body.add("title", title);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // music-server API 호출
            String url = musicServerUrl + "/api/analyze";
            log.info("music-server API 호출: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("music-server API 호출 실패: {}", response.getStatusCode());
                throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "오디오 분석 서버 오류");
            }

            // JSON 파싱
            JsonNode result = objectMapper.readTree(response.getBody());

            if (!result.has("success") || !result.get("success").asBoolean()) {
                String errorMsg = result.has("error") ? result.get("error").asText() : "알 수 없는 오류";
                throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "오디오 분석 실패: " + errorMsg);
            }

            log.info("music-server 분석 완료: title={}", title);
            return result;

        } catch (IOException e) {
            log.error("music-server API 호출 중 오류: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "오디오 분석 중 오류: " + e.getMessage());
        }
    }
}
