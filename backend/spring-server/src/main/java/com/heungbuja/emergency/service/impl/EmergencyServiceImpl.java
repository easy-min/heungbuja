package com.heungbuja.emergency.service.impl;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.service.AdminService;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.emergency.dto.EmergencyRequest;
import com.heungbuja.emergency.dto.EmergencyResponse;
import com.heungbuja.emergency.entity.EmergencyReport;
import com.heungbuja.emergency.repository.EmergencyReportRepository;
import com.heungbuja.emergency.service.EmergencyNotificationService;
import com.heungbuja.emergency.service.EmergencyService;
import com.heungbuja.session.enums.ActivityType;
import com.heungbuja.session.service.SessionStateService;
import com.heungbuja.session.state.ActivityState;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * 응급 신고 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyServiceImpl implements EmergencyService {

    private final EmergencyReportRepository emergencyReportRepository;
    private final UserService userService;
    private final AdminService adminService;
    private final SessionStateService sessionStateService;
    private final EmergencyNotificationService notificationService;
    private final TaskScheduler taskScheduler;

    @Override
    @Transactional
    public EmergencyResponse detectEmergencyWithSchedule(EmergencyRequest request) {
        User user = userService.findById(request.getUserId());
        Long userId = user.getId();

        // 1. 현재 진행 중인 활동 중단
        interruptCurrentActivity(userId);

        // 2. 기존 PENDING 신고가 있는지 확인
        Optional<EmergencyReport> existingReport = emergencyReportRepository
                .findFirstByUserIdAndStatusOrderByReportedAtDesc(userId, EmergencyReport.ReportStatus.PENDING);

        if (existingReport.isPresent()) {
            // 중복 응급 신호 = 정말 응급 상황 → 즉시 확정
            log.info("중복 응급 신호 감지, 기존 신고 즉시 확정: reportId={}, userId={}",
                    existingReport.get().getId(), userId);
            return confirmRecentReport(userId);
        }

        // 3. 응급 신고 생성 및 저장
        EmergencyReport savedReport = createEmergencyReport(user, request);

        // 4. 응급 상태로 설정
        sessionStateService.setCurrentActivity(userId, ActivityState.emergency(savedReport.getId()));

        String message = "괜찮으세요? 정말 신고가 필요하신가요?";

        log.info("응급 신고 감지: reportId={}, userId={}, 60초 후 자동 확정 스케줄",
                savedReport.getId(), userId);

        // 5. 트랜잭션 커밋 후 스케줄 등록
        scheduleAutoConfirmAfterCommit(savedReport.getId());

        return EmergencyResponse.from(savedReport, message);
    }

    /**
     * 현재 진행 중인 활동 중단
     */
    private void interruptCurrentActivity(Long userId) {
        ActivityState currentActivity = sessionStateService.getCurrentActivity(userId);

        if (currentActivity.getType() == ActivityType.IDLE) {
            return; // 진행 중인 활동 없음
        }

        log.info("응급신호로 현재 활동 중단: userId={}, activityType={}, sessionId={}",
                userId, currentActivity.getType(), currentActivity.getSessionId());

        switch (currentActivity.getType()) {
            case GAME:
                // 게임 즉시 중단
                String sessionId = currentActivity.getSessionId();
                if (sessionStateService.trySetInterrupt(sessionId, "EMERGENCY")) {
                    sessionStateService.setSessionStatus(sessionId, "EMERGENCY_INTERRUPT");
                    log.info("응급신호로 게임 중단 플래그 설정: sessionId={}", sessionId);
                }
                break;

            case MUSIC:
                // 음악 즉시 중단
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

    /**
     * 응급 신고 생성 및 저장
     */
    private EmergencyReport createEmergencyReport(User user, EmergencyRequest request) {
        EmergencyReport report = EmergencyReport.builder()
                .user(user)
                .triggerWord(request.getTriggerWord())
                .fullText(request.getFullText())  // 전체 발화 텍스트 저장
                .isConfirmed(false)
                .status(EmergencyReport.ReportStatus.PENDING)
                .reportedAt(LocalDateTime.now())
                .build();

        return emergencyReportRepository.save(report);
    }

    /**
     * 트랜잭션 커밋 후 자동 확정 스케줄 등록
     */
    private void scheduleAutoConfirmAfterCommit(Long reportId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("트랜잭션 커밋 완료, 스케줄 등록: reportId={}", reportId);
                scheduleAutoConfirm(reportId, 60);
            }
        });
    }

    @Override
    @Async
    public void scheduleAutoConfirm(Long reportId, int secondsDelay) {
        log.info("응급 신고 자동 확정 스케줄 등록: reportId={}, delay={}초", reportId, secondsDelay);
        taskScheduler.schedule(
                () -> autoConfirm(reportId),
                java.util.Date.from(java.time.Instant.now().plusSeconds(secondsDelay))
        );
    }

    /**
     * 자동 확정 실행 (스케줄러 호출)
     */
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
        emergencyReportRepository.save(report); // 명시적 저장 (TaskScheduler 스레드에서 트랜잭션 보장)
        log.info("응급 신고 확정됨: reportId={}, status={}", reportId, report.getStatus());

        // WebSocket으로 관리자에게 알림 전송
        notificationService.sendEmergencyAlert(report);
        log.info("관리자에게 WebSocket 알림 전송 완료: reportId={}", reportId);
    }

    @Override
    @Transactional
    public void cancelReport(Long reportId) {
        EmergencyReport report = findById(reportId);
        report.cancel();
        emergencyReportRepository.save(report);
    }

    @Override
    @Transactional
    public EmergencyResponse cancelRecentReport(Long userId) {
        log.info("응급 신고 취소 요청: userId={}", userId);

        EmergencyReport report = emergencyReportRepository
                .findFirstByUserIdAndStatusOrderByReportedAtDesc(userId, EmergencyReport.ReportStatus.PENDING)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND,
                        "취소할 응급 신고가 없습니다"));

        report.cancel();
        emergencyReportRepository.save(report);
        log.info("응급 신고 취소됨: reportId={}, status={}", report.getId(), report.getStatus());

        // 응급 상태 해제 (IDLE로 전환)
        sessionStateService.clearActivity(userId);
        log.info("응급 상태 해제: userId={}", userId);

        return EmergencyResponse.from(report, "괜찮으시군요. 신고를 취소했습니다");
    }

    @Override
    @Transactional
    public EmergencyResponse confirmRecentReport(Long userId) {
        log.info("응급 신고 즉시 확정 요청: userId={}", userId);

        EmergencyReport report = emergencyReportRepository
                .findFirstByUserIdAndStatusOrderByReportedAtDesc(userId, EmergencyReport.ReportStatus.PENDING)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND,
                        "확정할 응급 신고가 없습니다"));

        report.confirm();
        emergencyReportRepository.save(report);
        log.info("응급 신고 확정됨: reportId={}, status={}", report.getId(), report.getStatus());

        // 관리자에게 알림 전송
        notificationService.sendEmergencyAlert(report);

        return EmergencyResponse.from(report, "알겠습니다. 지금 바로 신고하겠습니다");
    }

    @Override
    @Transactional
    public EmergencyResponse confirmReport(Long reportId) {
        // Lazy Loading 방지: User와 Admin까지 fetch
        EmergencyReport report = emergencyReportRepository.findByIdWithUserAndAdmin(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND));
        report.confirm();
        emergencyReportRepository.save(report);
        notificationService.sendEmergencyAlert(report);

        return EmergencyResponse.from(report, "관리자에게 알림이 전송되었습니다");
    }

    @Override
    @Transactional
    public EmergencyResponse handleReport(Long adminId, Long reportId, String notes) {
        // Lazy Loading 방지: User와 Admin까지 fetch
        EmergencyReport report = emergencyReportRepository.findByIdWithUserAndAdmin(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND));
        Admin admin = adminService.findById(adminId);

        report.handle(admin, notes);
        EmergencyReport savedReport = emergencyReportRepository.save(report);

        return EmergencyResponse.from(savedReport, null);
    }

    @Override
    public List<EmergencyResponse> getConfirmedReports() {
        return emergencyReportRepository
                .findAllWithUser()  // User를 함께 fetch하여 LazyInitializationException 방지
                .stream()
                .filter(report -> report.getStatus() != EmergencyReport.ReportStatus.PENDING)  // PENDING 제외
                .sorted((a, b) -> b.getReportedAt().compareTo(a.getReportedAt()))  // 최신순 정렬
                .map(report -> EmergencyResponse.from(report, null))
                .collect(Collectors.toList());
    }

    @Override
    public EmergencyReport findById(Long reportId) {
        return emergencyReportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMERGENCY_NOT_FOUND));
    }
}
