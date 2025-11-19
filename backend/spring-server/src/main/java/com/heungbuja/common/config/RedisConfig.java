package com.heungbuja.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heungbuja.game.state.GameSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.context.annotation.Primary;
import com.heungbuja.game.state.GameState;


/**
 * Redis 설정
 *
 * - RedisTemplate 구성
 * - JSON 직렬화/역직렬화
 * - Redis Repository 활성화
 */
@Configuration
//@EnableRedisRepositories(basePackages = "com.heungbuja.*.repository")
public class RedisConfig {

    /**
     * RedisTemplate 빈 생성 (범용)
     *
     * Key: String
     * Value: JSON (Jackson)
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key/HashKey Serializer: String
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value/HashValue Serializer: JSON (Jackson)
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(createObjectMapper());

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * GameState 전용 RedisTemplate 빈 생성
     * SessionPrepareService에서 사용
     */
    @Bean
    public RedisTemplate<String, GameState> gameStateRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, GameState> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 범용 설정과 동일한 Serializer를 사용
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(createObjectMapper()));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(createObjectMapper()));

        template.afterPropertiesSet();
        return template;
    }

    /**
     * GameState 전용 RedisTemplate 빈 생성
     * GameService에서 사용
     */
    @Bean
    public RedisTemplate<String, GameSession> gameSessionRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, GameSession> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 범용 설정과 동일한 Serializer를 사용
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(createObjectMapper()));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(createObjectMapper()));

        template.afterPropertiesSet();
        return template;
    }


    /**
     * SongGameData 전용 RedisTemplate 빈 생성
     * SongGameDataCache에서 사용
     */
    @Bean
    public RedisTemplate<String, com.heungbuja.song.dto.SongGameData> songGameDataRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, com.heungbuja.song.dto.SongGameData> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key Serializer: String
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value Serializer: JSON (Jackson)
        ObjectMapper objectMapper = createObjectMapper();

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * ObjectMapper 생성 헬퍼 메서드
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 타입 정보 활성화 (역직렬화 시 올바른 클래스로 변환)
        // PROPERTY 방식: @class 속성으로 타입 정보 저장 (WRAPPER_ARRAY 대신)
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();

        objectMapper.activateDefaultTyping(
                typeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return objectMapper;
    }
}
