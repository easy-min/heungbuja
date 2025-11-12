package com.heungbuja.emergency.service.impl;

import com.heungbuja.common.websocket.EmergencyAlertMessage;
import com.heungbuja.emergency.entity.EmergencyReport;
import com.heungbuja.emergency.service.EmergencyNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 응급 알림 서비스 구현체
 * WebSocket을 통한 관리자 알림 전송 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyNotificationServiceImpl implements EmergencyNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendEmergencyAlert(EmergencyReport report) {
        // Admin이 연결되어 있는지 확인
        if (report.getUser().getAdmin() == null) {
            log.error("❌ WebSocket 알림 전송 실패: User(id={})에 Admin이 연결되어 있지 않습니다",
                    report.getUser().getId());
            return;
        }

        Long adminId = report.getUser().getAdmin().getId();

        // Null-safe 처리: 혹시 모를 null 값을 기본값으로 대체
        String userName = report.getUser().getName() != null
                ? report.getUser().getName()
                : "알 수 없음";
        String triggerWord = report.getTriggerWord() != null
                ? report.getTriggerWord()
                : "미상";

        // fullText도 null-safe 처리
        String fullText = report.getFullText() != null
                ? report.getFullText()
                : triggerWord;  // fullText가 없으면 triggerWord 사용

        EmergencyAlertMessage message = EmergencyAlertMessage.from(
                report.getId(),
                report.getUser().getId(),
                userName,
                triggerWord,
                fullText,
                report.getReportedAt(),
                report.getStatus().name()  // 신고 상태 추가
        );

        String destination = "/topic/admin/" + adminId + "/emergency";

        log.info("WebSocket 알림 전송: destination={}, reportId={}, userId={}, adminId={}",
                destination, report.getId(), report.getUser().getId(), adminId);

        // 특정 관리자에게만 전송
        messagingTemplate.convertAndSend(destination, message);
    }
}
