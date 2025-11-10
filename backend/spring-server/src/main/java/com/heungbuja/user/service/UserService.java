package com.heungbuja.user.service;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.service.AdminService;
import com.heungbuja.device.entity.Device;
import com.heungbuja.device.entity.Device.DeviceStatus;
import com.heungbuja.device.service.DeviceService;
import com.heungbuja.user.dto.RecentActivityDto;
import com.heungbuja.user.dto.UserRegisterRequest;
import com.heungbuja.user.dto.UserResponse;
import com.heungbuja.user.dto.UserUpdateRequest;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.repository.UserRepository;
import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.voice.entity.VoiceCommand;
import com.heungbuja.voice.repository.VoiceCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DeviceService deviceService;
    private final AdminService adminService;
    private final VoiceCommandRepository voiceCommandRepository;

    @Transactional
    public UserResponse registerUser(Long adminId, UserRegisterRequest request) {
        Admin admin = adminService.findById(adminId);
        Device device = deviceService.findById(request.getDeviceId());

        // 기기가 REGISTERED 상태인지 확인
        if (device.getStatus() != DeviceStatus.REGISTERED) {
            throw new CustomException(ErrorCode.DEVICE_ALREADY_ASSIGNED,
                    "Device must be in REGISTERED status to assign to a user");
        }

        // 기기가 이미 다른 활성 사용자에게 할당되어 있는지 확인
        userRepository.findByDeviceIdAndIsActive(device.getId(), true)
                .ifPresent(u -> {
                    throw new CustomException(ErrorCode.DEVICE_ALREADY_ASSIGNED);
                });

        User user = User.builder()
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .medicalNotes(request.getMedicalNotes())
                .emergencyContact(request.getEmergencyContact())
                .admin(admin)
                .isActive(true)
                .build();

        user.assignDevice(device);

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    public UserResponse getUserById(Long requesterId, Long userId) {
        User user = findById(userId);
        // 접근 권한 확인
        adminService.validateAdminAccess(requesterId, user.getAdmin().getId());
        return UserResponse.from(user);
    }

    public List<UserResponse> getUsersByAdmin(Long requesterId, Long adminId) {
        // 접근 권한 확인
        adminService.validateAdminAccess(requesterId, adminId);
        return userRepository.findByAdminId(adminId).stream()
                .map(this::enrichWithRecentActivities)
                .collect(Collectors.toList());
    }

    /**
     * User에 최근 활동 정보 추가
     */
    private UserResponse enrichWithRecentActivities(User user) {
        // 최근 3개의 음성 명령 조회
        List<VoiceCommand> recentCommands = voiceCommandRepository
                .findTop3ByUserIdOrderByCreatedAtDesc(user.getId());

        List<RecentActivityDto> activities = recentCommands.stream()
                .map(RecentActivityDto::from)
                .collect(Collectors.toList());

        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .medicalNotes(user.getMedicalNotes())
                .emergencyContact(user.getEmergencyContact())
                .deviceId(user.getDevice() != null ? user.getDevice().getId() : null)
                .isActive(user.getIsActive())
                .adminId(user.getAdmin().getId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .recentActivities(activities)
                .build();
    }

    public List<UserResponse> getActiveUsersByAdmin(Long requesterId, Long adminId) {
        // 접근 권한 확인
        adminService.validateAdminAccess(requesterId, adminId);
        return userRepository.findByAdminIdAndIsActive(adminId, true).stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse updateUser(Long requesterId, Long userId, UserUpdateRequest request) {
        User user = findById(userId);
        // 접근 권한 확인
        adminService.validateAdminAccess(requesterId, user.getAdmin().getId());

        user.updateInfo(
                request.getName(),
                request.getBirthDate(),
                request.getGender(),
                request.getMedicalNotes(),
                request.getEmergencyContact()
        );

        return UserResponse.from(user);
    }

    @Transactional
    public void deactivateUser(Long requesterId, Long userId) {
        User user = findById(userId);
        // 접근 권한 확인
        adminService.validateAdminAccess(requesterId, user.getAdmin().getId());
        user.deactivate();
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public User findByDeviceId(Long deviceId) {
        return userRepository.findByDeviceIdAndIsActive(deviceId, true)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND,
                        "No active user found for this device"));
    }
}
