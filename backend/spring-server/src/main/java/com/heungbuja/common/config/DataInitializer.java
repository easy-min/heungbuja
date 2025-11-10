package com.heungbuja.common.config;

import com.heungbuja.admin.entity.Admin;
import com.heungbuja.admin.entity.AdminRole;
import com.heungbuja.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // SUPER_ADMIN 계정이 없으면 생성
        if (!adminRepository.existsByUsername("superadmin")) {
            Admin superAdmin = Admin.builder()
                    .username("superadmin")
                    .password(passwordEncoder.encode("superadmin123!"))
                    .facilityName("흥부자 시스템 관리")
                    .contact("000-0000-0000")
                    .email("admin@heungbuja.com")
                    .role(AdminRole.SUPER_ADMIN)
                    .build();

            adminRepository.save(superAdmin);
            log.info("=================================================");
            log.info("SUPER_ADMIN 계정이 생성되었습니다.");
            log.info("Username: superadmin");
            log.info("Password: superadmin123!");
            log.info("⚠️  보안을 위해 초기 비밀번호를 변경하세요!");
            log.info("=================================================");
        } else {
            log.info("SUPER_ADMIN 계정이 이미 존재합니다.");
        }
    }
}
