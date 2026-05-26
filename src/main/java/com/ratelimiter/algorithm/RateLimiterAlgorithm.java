package com.ratelimiter.algorithm;

public interface RateLimiterAlgorithm {
    RateLimitResult check(String userId, String action);
}
