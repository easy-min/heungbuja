package com.heungbuja.emergency.controller;

import com.heungbuja.emergency.dto.EmergencyRequest;
import com.heungbuja.emergency.dto.EmergencyResponse;
import com.heungbuja.emergency.service.EmergencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/emergency")
@RequiredArgsConstructor
public class EmergencyController {

    private final EmergencyService emergencyService;

    @PostMapping
    public ResponseEntity<EmergencyResponse> detectEmergency(
            @Valid @RequestBody EmergencyRequest request) {
        EmergencyResponse response = emergencyService.detectEmergency(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelReport(@PathVariable Long id) {
        emergencyService.cancelReport(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<EmergencyResponse> confirmReport(@PathVariable Long id) {
        EmergencyResponse response = emergencyService.confirmReport(id);
        return ResponseEntity.ok(response);
    }

    // 관리자용 API
    @GetMapping("/admins/reports")
    public ResponseEntity<List<EmergencyResponse>> getConfirmedReports(Authentication authentication) {
        // TODO: 관리자 권한 확인
        List<EmergencyResponse> responses = emergencyService.getConfirmedReports();
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/admins/reports/{id}")
    public ResponseEntity<Void> handleReport(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String notes) {
        Long adminId = (Long) authentication.getPrincipal();
        emergencyService.handleReport(adminId, id, notes);
        return ResponseEntity.noContent().build();
    }
}
