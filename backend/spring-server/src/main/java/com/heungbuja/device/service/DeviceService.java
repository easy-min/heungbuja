package com.heungbuja.device.service;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.service.AdminService;
import com.heungbuja.device.dto.DeviceRegisterRequest;
import com.heungbuja.device.dto.DeviceResponse;
import com.heungbuja.device.dto.DeviceUpdateRequest;
import com.heungbuja.device.entity.Device;
import com.heungbuja.device.entity.Device.DeviceStatus;
import com.heungbuja.device.repository.DeviceRepository;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final AdminService adminService;

    @Transactional
    public DeviceResponse registerDevice(Long adminId, DeviceRegisterRequest request) {
        if (deviceRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new CustomException(ErrorCode.DEVICE_ALREADY_EXISTS);
        }

        Admin admin = adminService.findById(adminId);

        Device device = Device.builder()
                .serialNumber(request.getSerialNumber())
                .location(request.getLocation())
                .status(DeviceStatus.REGISTERED)
                .admin(admin)
                .build();

        Device savedDevice = deviceRepository.save(device);
        return DeviceResponse.from(savedDevice);
    }

    public DeviceResponse getDeviceById(Long requesterId, Long deviceId) {
        Device device = findById(deviceId);
        // 접근 권한 확인
        adminService.validateAdminAccess(requesterId, device.getAdmin().getId());
        return DeviceResponse.from(device);
    }

    public List<DeviceResponse> getDevicesByAdmin(Long requesterId, Long adminId, boolean availableOnly) {
        // 접근 권한 확인
        adminService.validateAdminAccess(requesterId, adminId);

        List<Device> devices = deviceRepository.findByAdminId(adminId);

        // availableOnly가 true면 userId가 null인 (연결되지 않은) 기기만 필터링
        if (availableOnly) {
            devices = devices.stream()
                    .filter(device -> device.getUser() == null)
                    .collect(Collectors.toList());
        }

        return devices.stream()
                .map(DeviceResponse::from)
                .collect(Collectors.toList());
    }

    public List<DeviceResponse> getDevicesByStatus(DeviceStatus status) {
        return deviceRepository.findByStatus(status).stream()
                .map(DeviceResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public DeviceResponse updateDevice(Long requesterId, Long deviceId, DeviceUpdateRequest request) {
        Device device = findById(deviceId);
        // 접근 권한 확인
        adminService.validateAdminAccess(requesterId, device.getAdmin().getId());

        if (request.getLocation() != null) {
            device.updateLocation(request.getLocation());
        }

        if (request.getStatus() != null) {
            device.updateStatus(request.getStatus());
        }

        return DeviceResponse.from(device);
    }

    @Transactional
    public void updateLastActiveAt(Long deviceId) {
        Device device = findById(deviceId);
        device.updateLastActiveAt();
    }

    public Device findById(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
    }

    public Device findBySerialNumber(String serialNumber) {
        return deviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
    }
}
