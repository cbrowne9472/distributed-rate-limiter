package com.ratelimiter.algorithm;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.List;

public class SlidingWindowRateLimiter {

    // Atomic Lua script: removes stale entries, counts current window, adds if under limit.
    // Uses a unique member per request (timestamp + nanoTime) so concurrent requests at
    // the same millisecond don't collide as sorted-set members.
    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local window_start = ARGV[2]
            local limit = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local member = ARGV[5]
            redis.call('ZREMRANGEBYSCORE', key, '-inf', '(' .. window_start)
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, ARGV[1], member)
                redis.call('EXPIRE', key, ttl)
                return {1, limit - count - 1}
            else
                return {0, 0}
            end
            """;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, List.class);

    private final StringRedisTemplate redis;
    private final int maxRequests;
    private final int windowSeconds;

    public SlidingWindowRateLimiter(StringRedisTemplate redis, int maxRequests, int windowSeconds) {
        this.redis = redis;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public RateLimitResult check(String userId, String action) {
        String key = "rate_limit:" + userId + ":" + action;
        long nowMillis = Instant.now().toEpochMilli();
        long windowStartMillis = nowMillis - (windowSeconds * 1000L);
        String member = nowMillis + "-" + Thread.currentThread().getId() + "-" + System.nanoTime();

        @SuppressWarnings("unchecked")
        List<Long> result = (List<Long>) redis.execute(
                SCRIPT,
                List.of(key),
                String.valueOf(nowMillis),
                String.valueOf(windowStartMillis),
                String.valueOf(maxRequests),
                String.valueOf(windowSeconds + 1),
                member
        );

        boolean allowed = result != null && result.get(0) == 1L;
        long remaining = (result != null) ? result.get(1) : 0L;

        return new RateLimitResult(allowed, (int) remaining, maxRequests);
    }
}
