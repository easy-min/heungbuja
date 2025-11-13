package com.heungbuja.admin.controller;

import com.heungbuja.admin.dto.AdminCreateRequest;
import com.heungbuja.admin.dto.AdminLoginRequest;
import com.heungbuja.admin.dto.AdminRegisterRequest;
import com.heungbuja.admin.dto.AdminResponse;
import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.service.AdminAuthService;
import com.heungbuja.admin.service.AdminAuthorizationService;
import com.heungbuja.admin.service.AdminService;
import com.heungbuja.auth.dto.TokenResponse;
import com.heungbuja.common.security.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}
