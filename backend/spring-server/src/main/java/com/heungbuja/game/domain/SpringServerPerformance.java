package com.heungbuja.game.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "spring_server_performance")
public class SpringServerPerformance {

    @Id
    private String id;

    private LocalDateTime timestamp;
    private Integer intervalSeconds;
    private Integer totalRequests;
    private Double averageResponseTimeMs;
    private Long minResponseTimeMs;
    private Long maxResponseTimeMs;
}
