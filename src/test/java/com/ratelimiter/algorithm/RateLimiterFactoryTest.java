package com.ratelimiter.algorithm;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RateLimiterFactoryTest {

    private final StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
    private final RateLimiterFactory factory = new RateLimiterFactory(mockRedis);

    @Test
    void createsSlidingWindowAlgorithm() {
        assertInstanceOf(SlidingWindowRateLimiter.class, factory.create("sliding_window", 10, 60));
    }

    @Test
    void createsTokenBucketAlgorithm() {
        assertInstanceOf(TokenBucketRateLimiter.class, factory.create("token_bucket", 10, 60));
    }

    @Test
    void isCaseInsensitive() {
        assertInstanceOf(SlidingWindowRateLimiter.class, factory.create("SLIDING_WINDOW", 10, 60));
        assertInstanceOf(TokenBucketRateLimiter.class, factory.create("Token_Bucket", 10, 60));
    }

    @Test
    void throwsOnUnknownAlgorithm() {
        assertThrows(IllegalArgumentException.class, () -> factory.create("fixed_window", 10, 60));
    }
}
