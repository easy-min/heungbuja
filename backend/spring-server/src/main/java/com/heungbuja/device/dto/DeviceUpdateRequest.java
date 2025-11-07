package com.heungbuja.device.dto;

import com.heungbuja.device.entity.Device.DeviceStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUpdateRequest {

    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;

    private DeviceStatus status;
}
