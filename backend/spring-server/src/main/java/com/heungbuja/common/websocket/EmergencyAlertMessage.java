package com.heungbuja.common.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class EmergencyAlertMessage {

    private String type;  // "EMERGENCY_REPORT"
    private Long reportId;
    private Long userId;
    private String userName;
    private String triggerWord;
    private String fullText;  // 전체 발화 텍스트
    private LocalDateTime reportedAt;
    private String status;  // 신고 상태 (PENDING, CONFIRMED, etc.)
    private String priority;  // "CRITICAL"

    public static EmergencyAlertMessage from(Long reportId, Long userId, String userName,
                                             String triggerWord, String fullText, LocalDateTime reportedAt, String status) {
        return EmergencyAlertMessage.builder()
                .type("EMERGENCY_REPORT")
                .reportId(reportId)
                .userId(userId)
                .userName(userName)
                .triggerWord(triggerWord)
                .fullText(fullText)
                .reportedAt(reportedAt)
                .status(status)
                .priority("CRITICAL")
                .build();
    }
}
