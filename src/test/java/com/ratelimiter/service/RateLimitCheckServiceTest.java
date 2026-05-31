package com.ratelimiter.service;

import com.ratelimiter.TestcontainersConfiguration;
import com.ratelimiter.algorithm.RateLimitResult;
import com.ratelimiter.model.UserTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RateLimitCheckServiceTest {

    @Autowired RateLimitCheckService checkService;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void flushRedis() {
        // Clear both rate limit state and cached rules between tests
        redis.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void freeUserAllowedAndShowsCorrectLimit() {
        RateLimitResult result = checkService.check("user1", UserTier.FREE, "api:get");

        assertTrue(result.allowed());
        assertEquals(100, result.limit());
        assertEquals(99,  result.remaining());
    }

    @Test
    void proUserGetsHigherLimit() {
        RateLimitResult result = checkService.check("user1", UserTier.PRO, "api:get");

        assertTrue(result.allowed());
        assertEquals(1000, result.limit());
        assertEquals(999,  result.remaining());
    }

    @Test
    void internalServiceGetsMaxLimit() {
        RateLimitResult result = checkService.check("svc1", UserTier.INTERNAL, "api:batch");

        assertTrue(result.allowed());
        assertEquals(Integer.MAX_VALUE, result.limit());
    }

    @Test
    void usersHaveIndependentCounters() {
        // Exhaust user1's FREE quota
        for (int i = 0; i < 100; i++) {
            checkService.check("user1", UserTier.FREE, "api:get");
        }
        assertFalse(checkService.check("user1", UserTier.FREE, "api:get").allowed());

        // user2 has their own fresh counter — should still be allowed
        assertTrue(checkService.check("user2", UserTier.FREE, "api:get").allowed());
    }

    @Test
    void ruleIsCachedInRedisAfterFirstCheck() {
        checkService.check("user1", UserTier.FREE, "api:get");

        Set<String> cacheKeys = redis.keys("rules::*");
        assertFalse(cacheKeys == null || cacheKeys.isEmpty(),
                "Expected at least one 'rules::' key in Redis after a check call");
    }
}
