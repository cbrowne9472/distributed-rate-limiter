package com.ratelimiter.algorithm;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RateLimiterFactory {

    private final StringRedisTemplate redis;

    public RateLimiterFactory(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public RateLimiterAlgorithm create(String algorithmType, int maxRequests, int windowSeconds) {
        return switch (algorithmType.toLowerCase()) {
            case "sliding_window" -> new SlidingWindowRateLimiter(redis, maxRequests, windowSeconds);
            case "token_bucket" -> new TokenBucketRateLimiter(redis, maxRequests, windowSeconds);
            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmType);
        };
    }
}
