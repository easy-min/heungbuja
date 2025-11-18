package com.heungbuja.admin.controller;

import com.heungbuja.activity.dto.ActivityLogResponse;
import com.heungbuja.activity.dto.ActivityStatsResponse;
import com.heungbuja.activity.entity.UserActivityLog;
import com.heungbuja.activity.enums.ActivityType;
import com.heungbuja.activity.service.ActivityLogService;
import com.heungbuja.admin.dto.*;
import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.service.AdminAuthService;
import com.heungbuja.admin.service.AdminAuthorizationService;
import com.heungbuja.admin.service.AdminService;
import com.heungbuja.admin.service.UserHealthMonitoringService;
import com.heungbuja.auth.dto.TokenResponse;
import com.heungbuja.common.security.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Admin API Controller
 * - 회원가입, 로그인: AdminAuthService
 * - 권한 검증: AdminAuthorizationService
 * - CRUD: AdminService
 */
@RestController
@RequestMapping("/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminService adminService;
    private final ActivityLogService activityLogService;
    private final UserHealthMonitoringService userHealthMonitoringService;

    /**
     * 관리자 회원가입
     * role은 자동으로 ADMIN으로 설정
     */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody AdminRegisterRequest request) {
        TokenResponse response = adminAuthService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 관리자 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        TokenResponse response = adminAuthService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * SUPER_ADMIN 전용: 새로운 관리자 생성
     * role을 지정하여 생성 가능
     */
    @PostMapping
    public ResponseEntity<AdminResponse> createAdmin(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody AdminCreateRequest request) {

        // SUPER_ADMIN 권한 체크
        adminAuthorizationService.requireSuperAdmin(principal.getId());

        // Admin 생성
        Admin admin = adminService.createAdmin(
                request.getUsername(),
                request.getPassword(),
                request.getFacilityName(),
                request.getContact(),
                request.getEmail(),
                request.getRole()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminResponse.from(admin));
    }

    /**
     * SUPER_ADMIN 전용: 모든 관리자 목록 조회 (페이징)
     */
    @GetMapping
    public ResponseEntity<Page<AdminResponse>> getAllAdmins(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        // SUPER_ADMIN 권한 체크
        adminAuthorizationService.requireSuperAdmin(principal.getId());

        // 페이징 조회
        Page<AdminResponse> responses = adminService.findAll(pageable)
                .map(AdminResponse::from);

        return ResponseEntity.ok(responses);
    }

    /**
     * Admin 삭제
     * Device나 User가 연결되어 있으면 삭제 불가
     */
    @DeleteMapping("/{adminId}")
    public ResponseEntity<Void> deleteAdmin(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long adminId) {

        // SUPER_ADMIN 권한 체크
        adminAuthorizationService.requireSuperAdmin(principal.getId());

        // Admin 삭제
        adminService.deleteAdmin(adminId);

        return ResponseEntity.noContent().build();
    }

    // ==================== 사용자 활동 로그 API ====================

    /**
     * 전체 활동 로그 조회 (페이징)
     * GET /admins/activity-logs
     * 모든 관리자 접근 가능 (@AuthenticationPrincipal로 자동 인증)
     */
    @GetMapping("/activity-logs")
    public ResponseEntity<Page<ActivityLogResponse>> getAllActivityLogs(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<ActivityLogResponse> response = activityLogService.findAllLogs(pageable)
                .map(ActivityLogResponse::from);

        return ResponseEntity.ok(response);
    }

    /**
     * 특정 사용자의 활동 로그 조회 (페이징)
     * GET /admins/activity-logs/users/{userId}
     * 모든 관리자 접근 가능
     */
    @GetMapping("/activity-logs/users/{userId}")
    public ResponseEntity<Page<ActivityLogResponse>> getActivityLogsByUser(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long userId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<ActivityLogResponse> response = activityLogService.findLogsByUserId(userId, pageable)
                .map(ActivityLogResponse::from);

        return ResponseEntity.ok(response);
    }

    /**
     * 활동 타입별 필터링 조회 (페이징)
     * GET /admins/activity-logs/filter
     * 모든 관리자 접근 가능
     */
    @GetMapping("/activity-logs/filter")
    public ResponseEntity<Page<ActivityLogResponse>> getActivityLogsByType(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(required = false) ActivityType activityType,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<UserActivityLog> logs;

        if (userId != null && activityType != null) {
            // 사용자 + 타입 필터링
            logs = activityLogService.findLogsByUserIdAndActivityType(userId, activityType, pageable);
        } else if (activityType != null) {
            // 타입만 필터링
            logs = activityLogService.findLogsByActivityType(activityType, pageable);
        } else if (userId != null) {
            // 사용자만 필터링
            logs = activityLogService.findLogsByUserId(userId, pageable);
        } else {
            // 전체 조회
            logs = activityLogService.findAllLogs(pageable);
        }

        Page<ActivityLogResponse> response = logs.map(ActivityLogResponse::from);

        return ResponseEntity.ok(response);
    }

    /**
     * 기간별 활동 로그 조회 (페이징)
     * GET /admins/activity-logs/range?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59
     * 모든 관리자 접근 가능
     */
    @GetMapping("/activity-logs/range")
    public ResponseEntity<Page<ActivityLogResponse>> getActivityLogsByDateRange(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<UserActivityLog> logs;

        if (userId != null) {
            logs = activityLogService.findLogsByUserIdAndDateRange(userId, startDate, endDate, pageable);
        } else {
            logs = activityLogService.findLogsByDateRange(startDate, endDate, pageable);
        }

        Page<ActivityLogResponse> response = logs.map(ActivityLogResponse::from);

        return ResponseEntity.ok(response);
    }

    /**
     * 활동 타입별 통계 조회 (일별/주별)
     * GET /admins/activity-logs/stats?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59
     * 모든 관리자 접근 가능
     */
    @GetMapping("/activity-logs/stats")
    public ResponseEntity<ActivityStatsResponse> getActivityStats(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Long userId) {

        Map<ActivityType, Long> stats;

        if (userId != null) {
            stats = activityLogService.getStatsByUserAndDateRange(userId, startDate, endDate);
        } else {
            stats = activityLogService.getStatsByDateRange(startDate, endDate);
        }

        return ResponseEntity.ok(ActivityStatsResponse.from(stats));
    }

    // ==================== 사용자 건강 모니터링 API ====================

    /**
     * 사용자별 게임 통계 조회
     * GET /admins/users/{userId}/game-stats
     * 모든 관리자 접근 가능
     */
    @GetMapping("/users/{userId}/game-stats")
    public ResponseEntity<UserGameStatsResponse> getUserGameStats(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long userId) {

        UserGameStatsResponse response = userHealthMonitoringService.getUserGameStats(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 동작별 수행도 분석 조회
     * GET /admins/users/{userId}/action-performance?periodDays=7
     * 모든 관리자 접근 가능
     * periodDays: 조회 기간 (null이면 전체 기간)
     */
    @GetMapping("/users/{userId}/action-performance")
    public ResponseEntity<ActionPerformanceResponse> getActionPerformance(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long userId,
            @RequestParam(required = false) Integer periodDays) {

        ActionPerformanceResponse response = userHealthMonitoringService.getActionPerformance(userId, periodDays);
        return ResponseEntity.ok(response);
    }

    /**
     * 시간대별 활동 추이 조회
     * GET /admins/users/{userId}/activity-trend?periodDays=7
     * 모든 관리자 접근 가능
     * periodDays: 조회 기간 (기본값: 7일, 최대 30일 권장)
     */
    @GetMapping("/users/{userId}/activity-trend")
    public ResponseEntity<ActivityTrendResponse> getActivityTrend(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "7") Integer periodDays) {

        ActivityTrendResponse response = userHealthMonitoringService.getActivityTrend(userId, periodDays);
        return ResponseEntity.ok(response);
    }
}
