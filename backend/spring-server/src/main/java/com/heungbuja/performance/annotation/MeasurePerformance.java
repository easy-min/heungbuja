package com.heungbuja.performance.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 실행 시간을 자동으로 측정하는 어노테이션
 *
 * 사용법:
 * @MeasurePerformance(component = "STT")
 * public String transcribe(byte[] audioData) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeasurePerformance {

    /**
     * 측정 대상 컴포넌트명
     * 예: "STT", "GPT", "SongSearch", "TTS"
     */
    String component();

    /**
     * 상세 설명 (선택적)
     */
    String description() default "";

    /**
     * DB에 저장할지 여부 (기본: true)
     */
    boolean saveToDb() default true;

    /**
     * 로그에 출력할지 여부 (기본: true)
     */
    boolean logToConsole() default true;
}
