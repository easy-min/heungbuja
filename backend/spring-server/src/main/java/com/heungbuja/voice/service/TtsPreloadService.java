package com.heungbuja.voice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TTS 고정 응답 사전 생성 서비스
 * 서버 시작 시 자주 사용되는 고정 응답을 미리 TTS로 변환하여 캐싱
 * 이를 통해 실제 사용 시 TTS API 호출 없이 캐시에서 바로 응답 가능
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("prod") // 운영 환경에서만 실행
public class TtsPreloadService {

    private final TtsCacheService ttsCacheService;
    private final TtsService ttsService;

    /**
     * 서버 시작 시 고정 응답 사전 생성
     * 비동기로 실행하여 서버 시작 속도에 영향 최소화
     */
    @PostConstruct
    public void preloadFixedResponses() {
        // 비동기로 실행 (서버 시작을 블로킹하지 않음)
        CompletableFuture.runAsync(() -> {
            try {
                log.info("=== TTS 고정 응답 사전 생성 시작 ===");
                long startTime = System.currentTimeMillis();

                Map<String, String> fixedResponses = getFixedResponses();
                AtomicInteger cached = new AtomicInteger(0);
                AtomicInteger generated = new AtomicInteger(0);

                // 각 고정 응답에 대해 캐시 확인 및 생성
                fixedResponses.forEach((text, voiceType) -> {
                    try {
                        // 이미 캐시에 있는지 확인
                        if (ttsCacheService.exists(text, voiceType)) {
                            cached.incrementAndGet();
                            log.debug("TTS 캐시 존재: '{}'", text);
                        } else {
                            // 캐시에 없으면 생성 (자동으로 캐시에 저장됨)
                            ttsService.synthesizeBytes(text, voiceType);
                            generated.incrementAndGet();
                            log.info("TTS 신규 생성: '{}'", text);
                        }
                    } catch (Exception e) {
                        log.error("TTS 사전 생성 실패: '{}' (voiceType: {})", text, voiceType, e);
                    }
                });

                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                log.info("=== TTS 고정 응답 사전 생성 완료 ===");
                log.info("- 총 개수: {}", fixedResponses.size());
                log.info("- 기존 캐시: {}", cached.get());
                log.info("- 신규 생성: {}", generated.get());
                log.info("- 소요 시간: {}ms", duration);
                log.info("- 예상 절감: API 호출 {} → {} ({}% 감소)",
                        fixedResponses.size(), generated.get(),
                        (100 - (generated.get() * 100 / fixedResponses.size())));
                log.info("====================================");

            } catch (Exception e) {
                log.error("TTS 사전 생성 중 예외 발생", e);
            }
        });
    }

    /**
     * 사전 생성할 고정 응답 목록
     * key: 응답 텍스트, value: 음성 타입
     */
    private Map<String, String> getFixedResponses() {
        Map<String, String> responses = new LinkedHashMap<>();

        // 재생 제어 (가장 자주 사용됨)
        responses.put("일시정지할게요", "default");
        responses.put("계속 재생할게요", "default");
        responses.put("다음 곡을 재생할게요", "default");
        responses.put("종료할게요", "default");

        // 연속 재생
        responses.put("비슷한 노래를 계속 들려드릴게요", "default");

        // 모드 전환
        responses.put("홈 화면으로 돌아갈게요", "default");
        responses.put("어떤 노래를 들려드릴까요?", "default");
        responses.put("체조를 시작할게요. 함께 운동해봐요!", "energetic");
        responses.put("수고하셨어요! 체조를 종료할게요", "calm");

        // 응급 상황 (긴급한 음성)
        responses.put("괜찮으세요? 대답해주세요!", "urgent");
        responses.put("괜찮으시군요. 신고를 취소했습니다", "calm");
        responses.put("알겠습니다. 지금 바로 신고하겠습니다", "urgent");

        // 에러 및 인식 불가 (차분한 음성)
        responses.put("죄송합니다. 다시 한번 말씀해주세요", "calm");
        responses.put("죄송합니다. 처리 중 문제가 발생했어요", "calm");
        responses.put("알 수 없는 명령입니다", "calm");

        // 기타
        responses.put("노래를 재생할게요", "default");

        return responses;
    }

    /**
     * 수동으로 특정 응답 캐시 갱신 (관리자 기능용)
     */
    public void regenerateResponse(String text, String voiceType) {
        log.info("TTS 캐시 수동 갱신: text='{}', voiceType='{}'", text, voiceType);
        ttsService.synthesizeBytes(text, voiceType);
    }

    /**
     * 모든 고정 응답 캐시 강제 재생성 (관리자 기능용)
     */
    public void regenerateAllResponses() {
        log.info("TTS 캐시 전체 재생성 시작");
        Map<String, String> fixedResponses = getFixedResponses();
        AtomicInteger count = new AtomicInteger(0);

        fixedResponses.forEach((text, voiceType) -> {
            try {
                ttsService.synthesizeBytes(text, voiceType);
                count.incrementAndGet();
            } catch (Exception e) {
                log.error("TTS 재생성 실패: '{}'", text, e);
            }
        });

        log.info("TTS 캐시 전체 재생성 완료: {} / {}", count.get(), fixedResponses.size());
    }
}
