package com.heungbuja.device.controller;

import com.heungbuja.common.security.AdminPrincipal;
import com.heungbuja.device.dto.DeviceRegisterRequest;
import com.heungbuja.device.dto.DeviceResponse;
import com.heungbuja.device.dto.DeviceUpdateRequest;
import com.heungbuja.device.entity.Device.DeviceStatus;
import com.heungbuja.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admins/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public ResponseEntity<DeviceResponse> registerDevice(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody DeviceRegisterRequest request) {
        Long adminId = principal.getId();
        DeviceResponse response = deviceService.registerDevice(adminId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceResponse> getDevice(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id) {
        Long requesterId = principal.getId();
        DeviceResponse response = deviceService.getDeviceById(requesterId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getDevices(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false, defaultValue = "false") boolean availableOnly) {
        Long requesterId = principal.getId();

        // adminId가 없으면 본인의 기기만 조회
        Long targetAdminId = adminId != null ? adminId : requesterId;
        List<DeviceResponse> responses = deviceService.getDevicesByAdmin(requesterId, targetAdminId, availableOnly);

        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceResponse> updateDevice(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody DeviceUpdateRequest request) {
        Long requesterId = principal.getId();
        DeviceResponse response = deviceService.updateDevice(requesterId, id, request);
        return ResponseEntity.ok(response);
    }
}
