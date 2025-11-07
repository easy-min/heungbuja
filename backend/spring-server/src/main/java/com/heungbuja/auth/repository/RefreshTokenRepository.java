package com.heungbuja.auth.repository;

import com.heungbuja.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUserId(Long userId);
    List<RefreshToken> findByDeviceId(Long deviceId);
    void deleteByUserId(Long userId);
    void deleteByDeviceId(Long deviceId);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
