package com.heungbuja.s3.repository;

import com.heungbuja.s3.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {
}