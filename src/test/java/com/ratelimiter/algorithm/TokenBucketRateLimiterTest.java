package com.ratelimiter.algorithm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class TokenBucketRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    static StringRedisTemplate redisTemplate;

    TokenBucketRateLimiter rateLimiter;

    @BeforeAll
    static void setUpRedis() {
        var config = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        var factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        factory.start();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.afterPropertiesSet();
    }

    @BeforeEach
    void setUp() {
        redisTemplate.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushAll();
            return null;
        });
        rateLimiter = new TokenBucketRateLimiter(redisTemplate, 5, 60);
    }

    @Test
    void allowsRequestsWithinCapacity() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.check("user1", "api:get").allowed());
        }
    }

    @Test
    void deniesRequestWhenBucketEmpty() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.check("user1", "api:post");
        }
        assertFalse(rateLimiter.check("user1", "api:post").allowed());
    }

    @Test
    void tracksRemainingTokens() {
        RateLimitResult first = rateLimiter.check("user1", "api:get");
        assertTrue(first.allowed());
        assertEquals(4, first.remaining());
    }

    @Test
    void isolatesUsersByKey() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.check("user1", "api:get");
        }
        assertTrue(rateLimiter.check("user2", "api:get").allowed());
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        // capacity=5, windowSeconds=1 → refill rate = 5 tokens/sec
        var fastRefill = new TokenBucketRateLimiter(redisTemplate, 5, 1);
        for (int i = 0; i < 5; i++) {
            fastRefill.check("user3", "api:get");
        }
        assertFalse(fastRefill.check("user3", "api:get").allowed());

        Thread.sleep(400); // 0.4s * 5 tokens/sec = 2 tokens refilled
        assertTrue(fastRefill.check("user3", "api:get").allowed());
    }
}
