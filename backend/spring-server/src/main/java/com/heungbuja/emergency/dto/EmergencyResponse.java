package com.heungbuja.emergency.dto;

import com.heungbuja.emergency.entity.EmergencyReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class EmergencyResponse {

    private Long reportId;
    private Long userId;
    private String userName;
    private String triggerWord;
    private Boolean isConfirmed;
    private String status;
    private LocalDateTime reportedAt;
    private String message;  // TTS 메시지

    public static EmergencyResponse from(EmergencyReport report, String message) {
        return EmergencyResponse.builder()
                .reportId(report.getId())
                .userId(report.getUser().getId())
                .userName(report.getUser().getName())
                .triggerWord(report.getTriggerWord())
                .isConfirmed(report.getIsConfirmed())
                .status(report.getStatus().name())
                .reportedAt(report.getReportedAt())
                .message(message)
                .build();
    }
}
