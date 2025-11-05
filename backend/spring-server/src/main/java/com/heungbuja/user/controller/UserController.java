package com.heungbuja.user.controller;

import com.heungbuja.user.dto.UserRegisterRequest;
import com.heungbuja.user.dto.UserResponse;
import com.heungbuja.user.dto.UserUpdateRequest;
import com.heungbuja.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admins/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> registerUser(
            Authentication authentication,
            @Valid @RequestBody UserRegisterRequest request) {
        Long adminId = (Long) authentication.getPrincipal();
        UserResponse response = userService.registerUser(adminId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(
            Authentication authentication,
            @PathVariable Long id) {
        Long requesterId = (Long) authentication.getPrincipal();
        UserResponse response = userService.getUserById(requesterId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers(
            Authentication authentication,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        Long requesterId = (Long) authentication.getPrincipal();

        // adminId가 없으면 본인의 어르신만 조회
        Long targetAdminId = adminId != null ? adminId : requesterId;

        List<UserResponse> responses;
        if (activeOnly) {
            responses = userService.getActiveUsersByAdmin(requesterId, targetAdminId);
        } else {
            responses = userService.getUsersByAdmin(requesterId, targetAdminId);
        }

        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        Long requesterId = (Long) authentication.getPrincipal();
        UserResponse response = userService.updateUser(requesterId, id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(
            Authentication authentication,
            @PathVariable Long id) {
        Long requesterId = (Long) authentication.getPrincipal();
        userService.deactivateUser(requesterId, id);
        return ResponseEntity.noContent().build();
    }
}
