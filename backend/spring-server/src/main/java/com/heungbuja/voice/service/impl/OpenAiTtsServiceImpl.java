package com.heungbuja.voice.service.impl;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.performance.annotation.MeasurePerformance;
import com.heungbuja.voice.service.TtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

/**
 * OpenAI TTS API를 사용한 음성 합성 서비스
 * GMS SSAFY 프록시를 통해 호출
 */
@Slf4j
@Service
@Primary // 이 구현체를 우선 사용
@Profile({"prod", "!local"}) // local 제외한 모든 프로파일
public class OpenAiTtsServiceImpl implements TtsService {

    @Value("${openai.gms.api-key}")
    private String gmsApiKey;

    @Value("${openai.gms.tts.url:https://gms.ssafy.io/gmsapi/api.openai.com/v1/audio/speech}")
    private String ttsApiUrl;

    @Value("${tts.storage.path:./tts-files}")
    private String storagePath;

    private final HttpClient httpClient;

    public OpenAiTtsServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String synthesize(String text, String voiceType) {
        log.info("OpenAI TTS 시작: text='{}', voiceType='{}'", text, voiceType);

        try {
            long startTime = System.currentTimeMillis();

            // 저장 디렉토리 생성
            Path storageDir = Paths.get(storagePath);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            // 음성 타입 매핑 (urgent → alloy, default → nova 등)
            String voice = mapVoiceType(voiceType);

            // JSON 요청 본문 생성 (제어 문자 이스케이프)
            String escapedText = escapeJson(text);
            String requestBody = String.format(
                    "{\"model\":\"gpt-4o-mini-tts\",\"input\":\"%s\",\"voice\":\"%s\",\"response_format\":\"mp3\"}",
                    escapedText, voice
            );

            // 디버깅 로그 추가
            log.info("===== TTS API 요청 상세 =====");
            log.info("URL: {}", ttsApiUrl);
            log.info("API Key 길이: {}", gmsApiKey.length());
            log.info("API Key (첫 20자): {}...", gmsApiKey.substring(0, Math.min(20, gmsApiKey.length())));
            log.info("Request Body: {}", requestBody);
            log.info("============================");

            // HTTP 요청 생성
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ttsApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + gmsApiKey)
                    .header("User-Agent", "HeungbujaApp/1.0")
                    .header("Accept", "*/*")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // API 호출
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            long endTime = System.currentTimeMillis();
            log.info("OpenAI TTS 완료: 소요 시간={}ms, Status Code={}",
                    endTime - startTime, response.statusCode());

            if (response.statusCode() == 200 && response.body() != null) {
                // 파일 저장
                String fileId = UUID.randomUUID().toString();
                String fileName = fileId + ".mp3";
                Path filePath = storageDir.resolve(fileName);

                Files.write(filePath, response.body());

                log.info("TTS 파일 저장 완료: fileId={}, 크기={} bytes",
                        fileId, response.body().length);

                return fileId;
            } else {
                String errorBody = new String(response.body());
                log.error("TTS API 응답 오류: Status={}, Body={}", response.statusCode(), errorBody);
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "TTS API 응답 오류: " + response.statusCode());
            }

        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            log.error("TTS API 네트워크 에러", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 합성 네트워크 오류: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("TTS API 호출 중단", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 합성 호출이 중단되었습니다");
        } catch (Exception e) {
            log.error("OpenAI TTS 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 합성에 실패했습니다: " + e.getMessage());
        }
    }

    @Override
    public byte[] getAudioFile(String fileId) {
        try {
            Path filePath = Paths.get(storagePath, fileId + ".mp3");

            if (!Files.exists(filePath)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "TTS 파일을 찾을 수 없습니다");
            }

            return Files.readAllBytes(filePath);

        } catch (IOException e) {
            log.error("TTS 파일 읽기 실패: fileId={}", fileId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "TTS 파일을 읽을 수 없습니다");
        }
    }

    @Override
    @MeasurePerformance(component = "TTS")
    public byte[] synthesizeBytes(String text, String voiceType) {
        log.info("OpenAI TTS (직접 반환) 시작: text='{}', voiceType='{}'", text, voiceType);

        try {
            long startTime = System.currentTimeMillis();

            // 음성 타입 매핑
            String voice = mapVoiceType(voiceType);

            // JSON 요청 본문 생성 (제어 문자 이스케이프)
            String escapedText = escapeJson(text);
            String requestBody = String.format(
                    "{\"model\":\"gpt-4o-mini-tts\",\"input\":\"%s\",\"voice\":\"%s\",\"response_format\":\"mp3\"}",
                    escapedText, voice
            );

            // HTTP 요청 생성
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ttsApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + gmsApiKey)
                    .header("User-Agent", "HeungbujaApp/1.0")
                    .header("Accept", "*/*")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // API 호출
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            long endTime = System.currentTimeMillis();
            log.info("OpenAI TTS (직접 반환) 완료: 소요 시간={}ms, Status Code={}, 크기={} bytes",
                    endTime - startTime, response.statusCode(), response.body().length);

            if (response.statusCode() == 200 && response.body() != null) {
                return response.body();
            } else {
                String errorBody = new String(response.body());
                log.error("TTS API 응답 오류: Status={}, Body={}", response.statusCode(), errorBody);
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "TTS API 응답 오류: " + response.statusCode());
            }

        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            log.error("TTS API 네트워크 에러", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 합성 네트워크 오류: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("TTS API 호출 중단", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 합성 호출이 중단되었습니다");
        } catch (Exception e) {
            log.error("OpenAI TTS 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "음성 합성에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * JSON 문자열 이스케이프 (줄바꿈, 탭, 따옴표 등)
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")  // 백슬래시
                .replace("\"", "\\\"")  // 따옴표
                .replace("\n", "\\n")   // 줄바꿈
                .replace("\r", "\\r")   // 캐리지 리턴
                .replace("\t", "\\t")   // 탭
                .replace("\b", "\\b")   // 백스페이스
                .replace("\f", "\\f");  // 폼 피드
    }

    /**
     * 음성 타입 매핑
     * OpenAI TTS는 alloy, echo, fable, onyx, nova, shimmer 지원
     */
    private String mapVoiceType(String voiceType) {
        if (voiceType == null || voiceType.equals("default")) {
            return "nova"; // 기본 음성 (여성, 따뜻함)
        }

        return switch (voiceType.toLowerCase()) {
            case "urgent", "emergency" -> "alloy"; // 긴급: 중성적이고 명확한 음성
            case "calm", "gentle" -> "shimmer"; // 부드럽고 차분한 음성
            case "energetic" -> "echo"; // 활기찬 음성
            case "male" -> "onyx"; // 남성 음성
            case "female" -> "nova"; // 여성 음성
            default -> "nova";
        };
    }
}
