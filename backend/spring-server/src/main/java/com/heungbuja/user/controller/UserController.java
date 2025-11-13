package com.heungbuja.user.controller;

import com.heungbuja.common.security.AdminPrincipal;
import com.heungbuja.user.dto.UserRegisterRequest;
import com.heungbuja.user.dto.UserResponse;
import com.heungbuja.user.dto.UserUpdateRequest;
import com.heungbuja.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admins/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> registerUser(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody UserRegisterRequest request) {
        Long adminId = principal.getId();
        UserResponse response = userService.registerUser(adminId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id) {
        Long requesterId = principal.getId();
        UserResponse response = userService.getUserById(requesterId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        Long requesterId = principal.getId();

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
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        Long requesterId = principal.getId();
        UserResponse response = userService.updateUser(requesterId, id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id) {
        Long requesterId = principal.getId();
        userService.deactivateUser(requesterId, id);
        return ResponseEntity.noContent().build();
    }
}
