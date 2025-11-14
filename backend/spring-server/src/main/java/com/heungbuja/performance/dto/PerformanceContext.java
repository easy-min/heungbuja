package com.heungbuja.performance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 요청 내 여러 성능 측정을 묶는 컨텍스트
 */
@Getter
@AllArgsConstructor
@Builder
public class PerformanceContext {

    private String requestId;
    private Long userId;
    private long startTime;
    private List<MeasurementRecord> records;

    public PerformanceContext(String requestId, Long userId) {
        this.requestId = requestId;
        this.userId = userId;
        this.startTime = System.currentTimeMillis();
        this.records = new ArrayList<>();
    }

    public void addRecord(String component, String methodName, long executionTime, boolean success, String errorMessage) {
        records.add(MeasurementRecord.builder()
            .component(component)
            .methodName(methodName)
            .executionTimeMs(executionTime)
            .success(success)
            .errorMessage(errorMessage)
            .build());
    }

    public long getTotalElapsed() {
        return System.currentTimeMillis() - startTime;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class MeasurementRecord {
        private String component;
        private String methodName;
        private Long executionTimeMs;
        private Boolean success;
        private String errorMessage;
    }
}
