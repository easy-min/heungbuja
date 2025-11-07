package com.heungbuja.command.controller;

import com.heungbuja.command.dto.CommandRequest;
import com.heungbuja.command.dto.CommandResponse;
import com.heungbuja.command.service.CommandService;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.voice.service.SttService;
import com.heungbuja.voice.service.TtsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 통합 음성 명령 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/commands")
@RequiredArgsConstructor
public class CommandController {

    private final CommandService commandService;
    private final SttService sttService;
    private final TtsService ttsService;

    /**
     * 음성 파일로 명령 처리 (통합 엔드포인트)
     * 음성 업로드 → STT → Intent 분석 → 실행 → TTS 응답
     * JWT 인증 필요 (Authorization: Bearer <token>)
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommandResponse> processVoiceCommand(
            @RequestParam("audioFile") MultipartFile audioFile) {

        // JWT 토큰에서 userId 추출 (보안 강화)
        Long userId = getCurrentUserId();

        log.info("음성 명령 처리 요청: userId={}, 파일크기={} bytes", userId, audioFile.getSize());

        try {
            // 1. STT: 음성 → 텍스트
            String transcribedText = sttService.transcribe(audioFile);
            log.info("STT 변환 완료: text='{}'", transcribedText);

            // 2. 텍스트 명령 처리
            CommandRequest request = CommandRequest.builder()
                    .userId(userId)
                    .text(transcribedText)
                    .build();

            CommandResponse response = commandService.processTextCommand(request);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("음성 명령 처리 실패");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CommandResponse.failure(null, "처리 중 오류가 발생했습니다", null));
        }
    }

    /**
     * 텍스트 명령 직접 처리 (디버깅용)
     */
    @PostMapping("/text")
    public ResponseEntity<CommandResponse> processTextCommand(
            @Valid @RequestBody CommandRequest request) {

        log.info("텍스트 명령 처리 요청: userId={}, text='{}'", request.getUserId(), request.getText());

        CommandResponse response = commandService.processTextCommand(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 음성 파일로 명령 처리 (음성 직접 응답)
     * 음성 업로드 → STT → Intent 분석 → 실행 → TTS 응답 (MP3 바이너리 직접 전송)
     * 메타데이터는 HTTP 헤더로 전송
     * JWT 인증 필요 (Authorization: Bearer <token>)
     */
    @PostMapping(value = "/process-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> processVoiceCommandWithAudio(
            @RequestParam("audioFile") MultipartFile audioFile) {

        // JWT 토큰에서 userId 추출 (보안 강화)
        Long userId = getCurrentUserId();

        log.info("음성 명령 처리 요청 (음성 직접 응답): userId={}, 파일크기={} bytes", userId, audioFile.getSize());

        try {
            // 1. STT: 음성 → 텍스트
            String transcribedText = sttService.transcribe(audioFile);
            log.info("STT 변환 완료: text='{}'", transcribedText);

            // 2. 텍스트 명령 처리
            CommandRequest request = CommandRequest.builder()
                    .userId(userId)
                    .text(transcribedText)
                    .build();

            CommandResponse response = commandService.processTextCommand(request);

            // 3. TTS: 텍스트 → 음성 (바이트 배열로 직접 반환)
            byte[] audioData = ttsService.synthesizeBytes(
                    response.getResponseText(),
                    response.getIntent().name().toLowerCase()
            );

            // 4. HTTP 헤더에 메타데이터 포함 (한글은 URL 인코딩)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.set("X-Success", String.valueOf(response.isSuccess()));
            headers.set("X-Intent", response.getIntent().name());

            // 한글 URL 인코딩
            try {
                headers.set("X-Response-Text", java.net.URLEncoder.encode(response.getResponseText(), "UTF-8"));
            } catch (Exception e) {
                log.error("Response text 인코딩 실패", e);
                headers.set("X-Response-Text", "");
            }

            // songInfo가 있으면 헤더에 추가 (한글 URL 인코딩)
            if (response.getSongInfo() != null) {
                try {
                    headers.set("X-Song-Title", java.net.URLEncoder.encode(response.getSongInfo().getTitle(), "UTF-8"));
                    headers.set("X-Song-Artist", java.net.URLEncoder.encode(response.getSongInfo().getArtist(), "UTF-8"));
                } catch (Exception e) {
                    log.error("Song info 인코딩 실패", e);
                }
            }

            log.info("음성 직접 응답 완료: intent={}, 오디오 크기={} bytes",
                    response.getIntent(), audioData.length);

            return new ResponseEntity<>(audioData, headers, HttpStatus.OK);

        } catch (CustomException e) {
            log.info("음성 명령 처리: errorCode={}, message={}",
                    e.getErrorCode(), e.getMessage());

            // CustomException의 ErrorCode에 따라 HTTP 상태 코드 결정
            HttpStatus httpStatus = e.getErrorCode().getStatus();
            String errorMessage = e.getMessage();

            // 에러 시에도 음성으로 응답
            try {
                byte[] errorAudio = ttsService.synthesizeBytes(errorMessage, "default");
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
                headers.set("X-Success", "false");
                headers.set("X-Error-Code", e.getErrorCode().name());

                // 한글 URL 인코딩
                try {
                    headers.set("X-Response-Text", java.net.URLEncoder.encode(errorMessage, "UTF-8"));
                } catch (Exception encodeError) {
                    headers.set("X-Response-Text", "Error");
                }

                return new ResponseEntity<>(errorAudio, headers, httpStatus);
            } catch (Exception ttsError) {
                log.error("에러 음성 생성 실패", ttsError);
                return ResponseEntity.status(httpStatus).build();
            }

        } catch (Exception e) {
            log.error("음성 명령 처리 실패 (Unknown Exception)", e);

            // 예상치 못한 에러는 500
            try {
                byte[] errorAudio = ttsService.synthesizeBytes("처리 중 오류가 발생했습니다", "default");
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
                headers.set("X-Success", "false");

                try {
                    headers.set("X-Response-Text", java.net.URLEncoder.encode("처리 중 오류가 발생했습니다", "UTF-8"));
                } catch (Exception encodeError) {
                    headers.set("X-Response-Text", "Error");
                }

                return new ResponseEntity<>(errorAudio, headers, HttpStatus.INTERNAL_SERVER_ERROR);
            } catch (Exception ttsError) {
                log.error("에러 음성 생성 실패", ttsError);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    /**
     * TTS 음성 파일 다운로드
     */
    @GetMapping("/tts/{fileId}")
    public ResponseEntity<byte[]> getTtsAudio(@PathVariable String fileId) {
        try {
            byte[] audioData = ttsService.getAudioFile(fileId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.setContentDispositionFormData("attachment", fileId + ".mp3");

            return new ResponseEntity<>(audioData, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("TTS 파일 다운로드 실패: fileId={}", fileId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * SecurityContext에서 현재 인증된 사용자 ID 추출
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(
                    com.heungbuja.common.exception.ErrorCode.UNAUTHORIZED,
                    "인증이 필요합니다"
            );
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }

        throw new CustomException(
                com.heungbuja.common.exception.ErrorCode.UNAUTHORIZED,
                "유효하지 않은 인증 정보입니다"
        );
    }
}
