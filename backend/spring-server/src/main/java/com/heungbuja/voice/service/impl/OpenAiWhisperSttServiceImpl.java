package com.heungbuja.voice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.voice.service.SttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * OpenAI Whisper API를 사용한 STT 서비스 구현
 * GMS SSAFY 프록시를 통해 호출
 */
@Slf4j
@Service
@Primary // 이 구현체를 우선 사용
@Profile({"prod", "!local"}) // local 제외한 모든 프로파일
public class OpenAiWhisperSttServiceImpl implements SttService {

    @Value("${openai.gms.api-key}")
    private String gmsApiKey;

    @Value("${openai.gms.stt.url:https://gms.ssafy.io/gmsapi/api.openai.com/v1/audio/transcriptions}")
    private String sttApiUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiWhisperSttServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String transcribe(MultipartFile audioFile) {
        if (!isSupportedFormat(audioFile)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "지원하지 않는 오디오 포맷입니다");
        }

        log.info("OpenAI Whisper STT 시작: 파일명={}, 크기={} bytes",
                audioFile.getOriginalFilename(), audioFile.getSize());

        try {
            long startTime = System.currentTimeMillis();

            // 디버깅 로그 추가
            log.info("===== STT API 요청 상세 =====");
            log.info("URL: {}", sttApiUrl);
            log.info("API Key 길이: {}", gmsApiKey.length());
            log.info("API Key (첫 20자): {}...", gmsApiKey.substring(0, Math.min(20, gmsApiKey.length())));
            log.info("File: {}, Size: {} bytes", audioFile.getOriginalFilename(), audioFile.getSize());
            log.info("Model: whisper-1, Language: ko");
            log.info("============================");

            // Multipart boundary 생성
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            byte[] multipartBody = buildMultipartBody(audioFile, boundary);

            // HTTP 요청 생성
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sttApiUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + gmsApiKey)
                    .header("User-Agent", "HeungbujaApp/1.0")
                    .header("Accept", "*/*")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            // API 호출
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long endTime = System.currentTimeMillis();
            log.info("OpenAI Whisper STT 완료: 소요 시간={}ms, Status Code={}",
                    endTime - startTime, response.statusCode());

            if (response.statusCode() == 200) {
                Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
                String text = (String) responseBody.get("text");
                log.info("STT 결과: '{}'", text);
                return text.trim();
            } else {
                log.error("STT API 응답 오류: Status={}, Body={}", response.statusCode(), response.body());
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "STT API 응답 오류: " + response.statusCode());
            }

        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            log.error("STT API 네트워크 에러", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 인식 네트워크 오류: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("STT API 호출 중단", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 인식 호출이 중단되었습니다");
        } catch (Exception e) {
            log.error("OpenAI Whisper STT 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 인식에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * Multipart/form-data 본문 생성
     */
    private byte[] buildMultipartBody(MultipartFile audioFile, String boundary) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String CRLF = "\r\n";

        // 파일 파트
        outputStream.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" +
                audioFile.getOriginalFilename() + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Type: " + audioFile.getContentType() + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        outputStream.write(audioFile.getBytes());
        outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));

        // model 파트
        outputStream.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"model\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        outputStream.write(("whisper-1" + CRLF).getBytes(StandardCharsets.UTF_8));

        // language 파트
        outputStream.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"language\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        outputStream.write(("ko" + CRLF).getBytes(StandardCharsets.UTF_8));

        // 종료 boundary
        outputStream.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));

        return outputStream.toByteArray();
    }
}
