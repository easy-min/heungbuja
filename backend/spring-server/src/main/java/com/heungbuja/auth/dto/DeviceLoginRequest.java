package com.heungbuja.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceLoginRequest {

    @NotBlank(message = "Serial number is required")
    private String serialNumber;
}
