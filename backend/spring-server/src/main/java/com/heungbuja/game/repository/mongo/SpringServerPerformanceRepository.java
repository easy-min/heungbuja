package com.heungbuja.game.repository.mongo;

import com.heungbuja.game.domain.SpringServerPerformance;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringServerPerformanceRepository extends MongoRepository<SpringServerPerformance, String> {
}
