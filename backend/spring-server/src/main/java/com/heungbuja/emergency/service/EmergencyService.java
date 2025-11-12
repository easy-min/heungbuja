package com.heungbuja.emergency.service;

import com.heungbuja.emergency.dto.EmergencyRequest;
import com.heungbuja.emergency.dto.EmergencyResponse;
import com.heungbuja.emergency.entity.EmergencyReport;

import java.util.List;

/**
 * 응급 신고 서비스 인터페이스
 */
public interface EmergencyService {

    /**
     * 긴급 신고 감지 및 처리 (스케줄 포함)
     */
    EmergencyResponse detectEmergencyWithSchedule(EmergencyRequest request);

    /**
     * 자동 확정 스케줄 등록
     */
    void scheduleAutoConfirm(Long reportId, int secondsDelay);

    /**
     * 신고 취소 (ID 기반)
     */
    void cancelReport(Long reportId);

    /**
     * 최근 신고 취소 (음성 명령용: "괜찮아")
     */
    EmergencyResponse cancelRecentReport(Long userId);

    /**
     * 최근 신고 즉시 확정 (음성 명령용: "안 괜찮아", "빨리 신고해")
     */
    EmergencyResponse confirmRecentReport(Long userId);

    /**
     * 신고 확정 (관리자 호출용)
     */
    EmergencyResponse confirmReport(Long reportId);

    /**
     * 관리자가 신고 처리
     */
    EmergencyResponse handleReport(Long adminId, Long reportId, String notes);

    /**
     * 신고 목록 조회 (관리자용, PENDING 제외)
     */
    List<EmergencyResponse> getConfirmedReports();

    /**
     * 신고 조회
     */
    EmergencyReport findById(Long reportId);
}
