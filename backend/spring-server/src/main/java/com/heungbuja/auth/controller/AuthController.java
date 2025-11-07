package com.heungbuja.auth.controller;

import com.heungbuja.auth.dto.DeviceLoginRequest;
import com.heungbuja.auth.dto.TokenRefreshRequest;
import com.heungbuja.auth.dto.TokenResponse;
import com.heungbuja.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/device")
    public ResponseEntity<TokenResponse> deviceLogin(@Valid @RequestBody DeviceLoginRequest request) {
        TokenResponse response = authService.deviceLogin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }
}
