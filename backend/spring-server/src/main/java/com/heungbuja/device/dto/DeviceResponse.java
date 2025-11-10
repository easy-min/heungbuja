package com.heungbuja.device.dto;

import com.heungbuja.device.entity.Device;
import com.heungbuja.device.entity.Device.DeviceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class DeviceResponse {

    private Long id;
    private String serialNumber;
    private String location;
    private DeviceStatus status;
    private LocalDateTime lastActiveAt;
    private Long adminId;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DeviceResponse from(Device device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .serialNumber(device.getSerialNumber())
                .location(device.getLocation())
                .status(device.getStatus())
                .lastActiveAt(device.getLastActiveAt())
                .adminId(device.getAdmin().getId())
                .userId(device.getUser() != null ? device.getUser().getId() : null)
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }
}
