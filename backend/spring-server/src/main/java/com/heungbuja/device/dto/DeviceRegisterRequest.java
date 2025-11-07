package com.heungbuja.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegisterRequest {

    @NotBlank(message = "Serial number is required")
    @Size(max = 50, message = "Serial number must not exceed 50 characters")
    private String serialNumber;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;
}
