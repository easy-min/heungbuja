package com.heungbuja.device.repository;

import com.heungbuja.device.entity.Device;
import com.heungbuja.device.entity.Device.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findBySerialNumber(String serialNumber);
    boolean existsBySerialNumber(String serialNumber);
    List<Device> findByAdminId(Long adminId);
    List<Device> findByStatus(DeviceStatus status);
    List<Device> findByAdminIdAndStatus(Long adminId, DeviceStatus status);
}
