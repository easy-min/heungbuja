package com.heungbuja.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "Invalid input value"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "External API error"),

    // Admin
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "Admin not found"),
    ADMIN_ALREADY_EXISTS(HttpStatus.CONFLICT, "Admin already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),

    // Device
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Device not found"),
    DEVICE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Device already exists"),
    DEVICE_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Device is already assigned to another user"),
    DEVICE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "Device is not in active status"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    USER_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "User is not active"),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "Expired token"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Refresh token not found"),

    // Authorization
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),

    // Song
    SONG_NOT_FOUND(HttpStatus.NOT_FOUND, "Song not found"),

    // Voice & Command
    STT_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Speech-to-text service error"),
    TTS_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Text-to-speech service error"),
    VOICE_RECOGNITION_FAILED(HttpStatus.BAD_REQUEST, "Voice recognition failed"),
    INTENT_UNKNOWN(HttpStatus.BAD_REQUEST, "Unable to understand the command"),
    COMMAND_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Command execution failed"),

    // Emergency
    EMERGENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "Emergency report not found"),

    // Media
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "Media not found");

    private final HttpStatus status;
    private final String message;
}
