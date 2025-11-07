package com.heungbuja.user.repository;

import com.heungbuja.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByAdminId(Long adminId);
    List<User> findByAdminIdAndIsActive(Long adminId, Boolean isActive);
    Optional<User> findByDeviceId(Long deviceId);
    Optional<User> findByDeviceIdAndIsActive(Long deviceId, Boolean isActive);
}
