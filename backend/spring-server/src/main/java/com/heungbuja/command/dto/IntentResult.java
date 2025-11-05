package com.heungbuja.command.dto;

import com.heungbuja.voice.enums.Intent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 의도 분석 결과
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentResult {

    private Intent intent;

    @Builder.Default
    private Map<String, String> entities = new HashMap<>();

    private Double confidence;

    // 원본 발화 텍스트 (전체)
    private String rawText;

    /**
     * 엔티티 추가
     */
    public void addEntity(String key, String value) {
        if (entities == null) {
            entities = new HashMap<>();
        }
        entities.put(key, value);
    }

    /**
     * 엔티티 값 가져오기
     */
    public String getEntity(String key) {
        return entities != null ? entities.get(key) : null;
    }

    /**
     * 간단한 결과 생성 (엔티티 없이)
     */
    public static IntentResult of(Intent intent) {
        return IntentResult.builder()
                .intent(intent)
                .confidence(1.0)
                .build();
    }

    /**
     * 엔티티와 함께 결과 생성
     */
    public static IntentResult withEntity(Intent intent, String key, String value) {
        Map<String, String> entities = new HashMap<>();
        entities.put(key, value);

        return IntentResult.builder()
                .intent(intent)
                .entities(entities)
                .confidence(1.0)
                .build();
    }
}
