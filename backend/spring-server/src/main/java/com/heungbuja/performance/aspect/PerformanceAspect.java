package com.heungbuja.performance.aspect;

import com.heungbuja.performance.annotation.MeasurePerformance;
import com.heungbuja.performance.dto.PerformanceContext;
import com.heungbuja.performance.service.PerformanceLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 성능 측정 AOP Aspect
 *
 * @MeasurePerformance 어노테이션이 붙은 메서드의 실행시간을 자동으로 측정합니다.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PerformanceAspect {

    private final PerformanceLogService performanceLogService;

    // ThreadLocal로 요청별 컨텍스트 관리
    private static final ThreadLocal<PerformanceContext> contextHolder = new ThreadLocal<>();

    /**
     * @MeasurePerformance 어노테이션이 붙은 메서드 실행 전후 처리
     */
    @Around("@annotation(com.heungbuja.performance.annotation.MeasurePerformance)")
    public Object measurePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        MeasurePerformance annotation = method.getAnnotation(MeasurePerformance.class);

        String component = annotation.component();
        String methodName = method.getName();

        // 컨텍스트 초기화 (최상위 측정인 경우)
        PerformanceContext context = contextHolder.get();
        boolean isRootMeasurement = false;

        if (context == null) {
            context = new PerformanceContext(UUID.randomUUID().toString(), null);
            contextHolder.set(context);
            isRootMeasurement = true;
        }

        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        Object result = null;

        try {
            // 실제 메서드 실행
            result = joinPoint.proceed();
            return result;

        } catch (Throwable e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;

        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // 컨텍스트에 기록 추가
            context.addRecord(component, methodName, executionTime, success, errorMessage);

            // 로그 출력
            if (annotation.logToConsole()) {
                logPerformance(component, methodName, executionTime, success, errorMessage);
            }

            // 최상위 측정이 끝나면 전체 요약 로그 출력, DB 저장 및 컨텍스트 정리
            if (isRootMeasurement) {
                logSummary(context);

                // DB에 비동기 저장
                if (annotation.saveToDb()) {
                    performanceLogService.saveContextAsync(context);
                }

                contextHolder.remove();
            }
        }
    }

    /**
     * 개별 측정 로그 출력
     */
    private void logPerformance(String component, String methodName, long executionTime, boolean success, String errorMessage) {
        if (success) {
            log.info("⏱️  [{}] {}: {}ms", component, methodName, executionTime);
        } else {
            log.warn("⚠️  [{}] {}: {}ms (실패: {})", component, methodName, executionTime, errorMessage);
        }
    }

    /**
     * 전체 요약 로그 출력 (예쁜 트리 형태)
     */
    private void logSummary(PerformanceContext context) {
        StringBuilder sb = new StringBuilder("\n");
        sb.append("┌─────────────────────────────────────────────────────────┐\n");
        sb.append("│ 🎯 성능 측정 결과                                       │\n");
        sb.append("├─────────────────────────────────────────────────────────┤\n");
        sb.append(String.format("│ 📌 Request ID: %-40s│\n", context.getRequestId()));
        sb.append(String.format("│ ⏱️  전체 소요시간: %-37dms │\n", context.getTotalElapsed()));
        sb.append("├─────────────────────────────────────────────────────────┤\n");

        long totalElapsed = context.getTotalElapsed();

        for (PerformanceContext.MeasurementRecord record : context.getRecords()) {
            double percentage = totalElapsed > 0 ? (record.getExecutionTimeMs() * 100.0 / totalElapsed) : 0;
            String status = record.getSuccess() ? "✅" : "❌";

            String line = String.format("│ %s [%-10s] %-20s %5dms (%5.1f%%) │",
                status,
                record.getComponent(),
                truncate(record.getMethodName(), 20),
                record.getExecutionTimeMs(),
                percentage
            );
            sb.append(line).append("\n");
        }

        sb.append("└─────────────────────────────────────────────────────────┘");

        log.info(sb.toString());
    }

    /**
     * 문자열 길이 제한
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) {
            return String.format("%-" + maxLength + "s", str);
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * 현재 스레드의 PerformanceContext 조회 (외부에서 사용)
     */
    public static PerformanceContext getCurrentContext() {
        return contextHolder.get();
    }

    /**
     * 수동으로 컨텍스트 설정 (Controller에서 userId 설정 등)
     */
    public static void setUserId(Long userId) {
        PerformanceContext context = contextHolder.get();
        if (context != null) {
            // userId는 불변이므로 새 컨텍스트 생성
            PerformanceContext newContext = PerformanceContext.builder()
                .requestId(context.getRequestId())
                .userId(userId)
                .startTime(context.getStartTime())
                .records(context.getRecords())
                .build();
            contextHolder.set(newContext);
        }
    }
}
