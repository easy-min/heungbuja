package com.heungbuja.emergency.service;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.service.AdminService;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.common.websocket.EmergencyAlertMessage;
import com.heungbuja.emergency.dto.EmergencyRequest;
import com.heungbuja.emergency.dto.EmergencyResponse;
import com.heungbuja.emergency.entity.EmergencyReport;
import com.heungbuja.emergency.repository.EmergencyReportRepository;
import com.heungbuja.game.service.GameService;
import com.heungbuja.session.enums.ActivityType;
import com.heungbuja.session.service.SessionStateService;
import com.heungbuja.session.state.ActivityState;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyService {

    private final EmergencyReportRepository emergencyReportRepository;
    private final UserService userService;
    private final AdminService adminService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionStateService sessionStateService;
    private final GameService gameService;

    // TaskScheduler Bean 등록 필요
    private final TaskScheduler taskScheduler;

    /**
     * 긴급 신고 감지 (스케줄 포함)
     */
    @Transactional
    public EmergencyResponse detectEmergencyWithSchedule(EmergencyRequest request) {
        User user = userService.findById(request.getUserId());
        Long userId = user.getId();

        // ===== 모든 진행 중인 활동 중단 (최우선) =====
        ActivityState currentActivity = sessionStateService.getCurrentActivity(userId);

        if (currentActivity.getType() != ActivityType.IDLE) {
            log.info("응급신호로 현재 활동 중단: userId={}, activityType={}, sessionId={}",
                    userId, currentActivity.getType(), currentActivity.getSessionId());

            switch (currentActivity.getType()) {
                case GAME:
                    // 게임 즉시 중단
                    String sessionId = currentActivity.getSessionId();
                    if (sessionStateService.trySetInterrupt(sessionId, "EMERGENCY")) {
                        // 비동기로 게임 종료 처리 (트랜잭션 분리)
                        // gameService.interruptGame(sessionId, "EMERGENCY"); // 동기 호출 시 순환 참조 위험
                        sessionStateService.setSessionStatus(sessionId, "EMERGENCY_INTERRUPT");
                        log.info("응급신호로 게임 중단 플래그 설정: sessionId={}", sessionId);
                    }
                    break;

                case MUSIC:
                    // 음악 즉시 중단
                    // TODO: 음악 서비스에 중단 로직 추가
                    log.info("응급신호로 음악 중단: userId={}", userId);
                    break;

                case EMERGENCY:
                    // 이미 응급 상황
                    log.warn("이미 응급 상황 진행 중: userId={}", userId);
                    break;

                default:
                    break;
            }

            // 현재 활동 상태 초기화
            sessionStateService.clearActivity(userId);
        }

        // ===== 응급신고 저장 =====
        EmergencyReport report = EmergencyReport.builder()
                .user(user)
                .triggerWord(request.getTriggerWord())
                .fullText(request.getFullText())  // 전체 발화 텍스트 저장
                .isConfirmed(false)
                .status(EmergencyReport.ReportStatus.PENDING)
                .reportedAt(LocalDateTime.now())
                .build();

        EmergencyReport savedReport = emergencyReportRepository.save(report);

        // 응급 상태로 설정
        sessionStateService.setCurrentActivity(userId, ActivityState.emergency(savedReport.getId()));

        String message = "괜찮으세요? 정말 신고가 필요하신가요?";

        log.info("응급 신고 감지: reportId={}, userId={}, 10초 후 자동 확정 스케줄",
                savedReport.getId(), userId);

        // 트랜잭션 커밋 후 스케줄 등록
        Long reportId = savedReport.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("트랜잭션 커밋 완료, 스케줄 등록: reportId={}", reportId);
                scheduleAutoConfirm(reportId, 10);
            }
        });

        return EmergencyResponse.from(savedReport, message);
    }

    /**
     * 10초 후 자동 confirm 스케줄
     */
    @Async
    public void scheduleAutoConfirm(Long reportId, int secondsDelay) {
        log.info("응급 신고 자동 확정 스케줄 등록: reportId={}, delay={}초", reportId, secondsDelay);
        taskScheduler.schedule(
                () -> autoConfirm(reportId),
                java.util.Date.from(java.time.Instant.now().plusSeconds(secondsDelay))
        );
    }

    @Transactional
    public void autoConfirm(Long reportId) {
        log.info("응급 신고 자동 확정 실행: reportId={}", reportId);

        // LazyInitializationException 방지: User와 Admin까지 fetch
        EmergencyReport report = emergencyReportRepository.findByIdWithUserAndAdmin(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND));

        // 이미 취소되었으면 confirm하지 않음
        if (report.getStatus() == EmergencyReport.ReportStatus.FALSE_ALARM) {
            log.info("응급 신고가 이미 취소됨: reportId={}, status={}", reportId, report.getStatus());
            return;
        }

        report.confirm(); // confirm + 상태 변경
        log.info("응급 신고 확정됨: reportId={}, status={}", reportId, report.getStatus());

        // WebSocket으로 관리자에게 알림 전송
        sendEmergencyAlert(report);
        log.info("관리자에게 WebSocket 알림 전송 완료: reportId={}", reportId);
    }

    /**
     * 신고 취소 (유저가 괜찮다고 응답)
     */
    @Transactional
    public void cancelReport(Long reportId) {
        EmergencyReport report = findById(reportId);
        report.cancel();
    }

    /**
     * 신고 취소 (음성 명령: "괜찮아")
     * 해당 사용자의 가장 최근 PENDING 신고를 취소
     */
    @Transactional
    public EmergencyResponse cancelRecentReport(Long userId) {
        log.info("응급 신고 취소 요청: userId={}", userId);

        EmergencyReport report = emergencyReportRepository
                .findFirstByUserIdAndStatusOrderByReportedAtDesc(userId, EmergencyReport.ReportStatus.PENDING)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND,
                        "취소할 응급 신고가 없습니다"));

        report.cancel();
        log.info("응급 신고 취소됨: reportId={}, status={}", report.getId(), report.getStatus());

        // 응급 상태 해제 (IDLE로 전환)
        sessionStateService.clearActivity(userId);
        log.info("응급 상태 해제: userId={}", userId);

        return EmergencyResponse.from(report, "괜찮으시군요. 신고를 취소했습니다");
    }

    /**
     * 신고 즉시 확정 (음성 명령: "안 괜찮아", "빨리 신고해")
     * 해당 사용자의 가장 최근 PENDING 신고를 즉시 확정
     */
    @Transactional
    public EmergencyResponse confirmRecentReport(Long userId) {
        log.info("응급 신고 즉시 확정 요청: userId={}", userId);

        EmergencyReport report = emergencyReportRepository
                .findFirstByUserIdAndStatusOrderByReportedAtDesc(userId, EmergencyReport.ReportStatus.PENDING)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND,
                        "확정할 응급 신고가 없습니다"));

        report.confirm();
        log.info("응급 신고 확정됨: reportId={}, status={}", report.getId(), report.getStatus());

        // 관리자에게 알림 전송
        sendEmergencyAlert(report);

        return EmergencyResponse.from(report, "알겠습니다. 지금 바로 신고하겠습니다");
    }

    /**
     * 신고 확정 (관리자 호출용)
     */
    @Transactional
    public EmergencyResponse confirmReport(Long reportId) {
        // Lazy Loading 방지: User와 Admin까지 fetch
        EmergencyReport report = emergencyReportRepository.findByIdWithUserAndAdmin(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND));
        report.confirm();
        sendEmergencyAlert(report);

        return EmergencyResponse.from(report, "관리자에게 알림이 전송되었습니다");
    }

    /**
     * WebSocket으로 긴급 알림 전송
     */
    private void sendEmergencyAlert(EmergencyReport report) {
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
                report.getReportedAt()
        );

        String destination = "/topic/admin/" + adminId + "/emergency";

        log.info("WebSocket 알림 전송: destination={}, reportId={}, userId={}, adminId={}",
                destination, report.getId(), report.getUser().getId(), adminId);

        // 특정 관리자에게만 전송
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * 관리자가 신고 처리
     */
    @Transactional
    public void handleReport(Long adminId, Long reportId, String notes) {
        // Lazy Loading 방지: User와 Admin까지 fetch
        EmergencyReport report = emergencyReportRepository.findByIdWithUserAndAdmin(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND));
        Admin admin = adminService.findById(adminId);

        report.handle(admin, notes);
    }

    /**
     * 신고 목록 조회 (관리자)
     * 모든 신고 조회 (PENDING, CONFIRMED, RESOLVED, FALSE_ALARM)
     */
    public List<EmergencyResponse> getConfirmedReports() {
        return emergencyReportRepository
                .findAllWithUser()  // User를 함께 fetch하여 LazyInitializationException 방지
                .stream()
                .sorted((a, b) -> b.getReportedAt().compareTo(a.getReportedAt()))  // 최신순 정렬
                .map(report -> EmergencyResponse.from(report, null))
                .collect(Collectors.toList());
    }

    /**
     * 신고 조회
     */
    public EmergencyReport findById(Long reportId) {
        return emergencyReportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND));
    }
}
