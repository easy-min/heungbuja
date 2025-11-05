package com.heungbuja.admin.controller;

import com.heungbuja.admin.dto.AdminCreateRequest;
import com.heungbuja.admin.dto.AdminLoginRequest;
import com.heungbuja.admin.dto.AdminRegisterRequest;
import com.heungbuja.admin.dto.AdminResponse;
import com.heungbuja.admin.service.AdminService;
import com.heungbuja.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody AdminRegisterRequest request) {
        TokenResponse response = adminService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        TokenResponse response = adminService.login(request);
        return ResponseEntity.ok(response);
    }

    // SUPER_ADMIN 전용: 새로운 관리자 생성
    @PostMapping
    public ResponseEntity<AdminResponse> createAdmin(
            Authentication authentication,
            @Valid @RequestBody AdminCreateRequest request) {
        Long adminId = (Long) authentication.getPrincipal();
        AdminResponse response = adminService.createAdmin(adminId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // SUPER_ADMIN 전용: 모든 관리자 목록 조회
    @GetMapping
    public ResponseEntity<List<AdminResponse>> getAllAdmins(Authentication authentication) {
        Long adminId = (Long) authentication.getPrincipal();
        List<AdminResponse> responses = adminService.getAllAdmins(adminId);
        return ResponseEntity.ok(responses);
    }
}
