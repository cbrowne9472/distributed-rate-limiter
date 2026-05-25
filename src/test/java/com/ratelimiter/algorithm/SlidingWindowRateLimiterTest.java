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
class SlidingWindowRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    static StringRedisTemplate redisTemplate;

    SlidingWindowRateLimiter rateLimiter;

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
        rateLimiter = new SlidingWindowRateLimiter(redisTemplate, 5, 10);
    }

    @Test
    void allowsRequestsUnderLimit() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.check("user1", "api:get").allowed());
        }
    }

    @Test
    void deniesRequestAtLimit() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.check("user1", "api:post");
        }
        RateLimitResult denied = rateLimiter.check("user1", "api:post");
        assertFalse(denied.allowed());
        assertEquals(0, denied.remaining());
    }

    @Test
    void tracksRemainingQuota() {
        RateLimitResult first = rateLimiter.check("user2", "api:get");
        assertTrue(first.allowed());
        assertEquals(4, first.remaining());

        RateLimitResult second = rateLimiter.check("user2", "api:get");
        assertTrue(second.allowed());
        assertEquals(3, second.remaining());
    }

    @Test
    void isolatesRequestsByUser() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.check("user1", "api:get");
        }
        assertTrue(rateLimiter.check("user2", "api:get").allowed());
    }

    @Test
    void isolatesRequestsByAction() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.check("user1", "api:get");
        }
        assertTrue(rateLimiter.check("user1", "api:post").allowed());
    }
}
