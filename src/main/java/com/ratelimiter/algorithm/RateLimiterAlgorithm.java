package com.ratelimiter.algorithm;

public interface RateLimiterAlgorithm {
    RateLimitResult check(String userId, String action);

    /** Read current remaining quota without consuming a token. */
    int remaining(String userId, String action);
}
