package com.heungbuja.emergency.service;

import com.heungbuja.emergency.entity.EmergencyReport;

/**
 * 응급 알림 서비스 인터페이스
 * WebSocket을 통한 관리자 알림 전송 담당
 */
public interface EmergencyNotificationService {

    /**
     * WebSocket으로 긴급 알림 전송
     *
     * @param report 응급 신고
     */
    void sendEmergencyAlert(EmergencyReport report);
}
