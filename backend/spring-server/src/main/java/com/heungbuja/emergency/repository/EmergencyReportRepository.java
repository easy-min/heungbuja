package com.heungbuja.emergency.repository;

import com.heungbuja.emergency.entity.EmergencyReport;
import com.heungbuja.emergency.entity.EmergencyReport.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;  // <-- 추가
import java.util.Optional;                       // <-- 추가
import java.util.List;

@Repository
public interface EmergencyReportRepository extends JpaRepository<EmergencyReport, Long> {

    // 특정 상태의 신고 조회
    List<EmergencyReport> findByStatusOrderByReportedAtDesc(ReportStatus status);

    // 특정 어르신의 신고 이력
    List<EmergencyReport> findByUserIdOrderByReportedAtDesc(Long userId);

    // 확정된 신고만 조회
    List<EmergencyReport> findByIsConfirmedTrueOrderByReportedAtDesc();

    // PENDING 상태 신고 개수
    long countByStatus(ReportStatus status);

    @Query("SELECT r FROM EmergencyReport r " +
            "JOIN FETCH r.user u " +
            "JOIN FETCH u.admin " +
            "WHERE r.id = :id")
    Optional<EmergencyReport> findByIdWithUserAndAdmin(@Param("id") Long id);

    // 모든 신고 조회 (User fetch)
    @Query("SELECT r FROM EmergencyReport r JOIN FETCH r.user")
    List<EmergencyReport> findAllWithUser();

    // 특정 사용자의 가장 최근 PENDING 신고 조회
    Optional<EmergencyReport> findFirstByUserIdAndStatusOrderByReportedAtDesc(Long userId, ReportStatus status);

    // 특정 사용자의 특정 상태 신고 모두 조회 (중복 생성 방지용)
    List<EmergencyReport> findAllByUserIdAndStatus(Long userId, ReportStatus status);

}
