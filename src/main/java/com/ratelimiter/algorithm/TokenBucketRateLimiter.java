package com.ratelimiter.algorithm;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.List;

public class TokenBucketRateLimiter implements RateLimiterAlgorithm {

    // Atomic Lua: refill tokens based on elapsed time since last request, then consume one.
    // State stored as a Redis hash: {tokens, last_refill} per user+action key.
    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_rate = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1])
            local last_refill = tonumber(data[2])
            if tokens == nil then
                tokens = capacity
                last_refill = now
            end
            local elapsed = (now - last_refill) / 1000.0
            tokens = math.min(capacity, tokens + elapsed * refill_rate)
            local allowed
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            else
                allowed = 0
            end
            redis.call('HMSET', key, 'tokens', tostring(tokens), 'last_refill', tostring(now))
            redis.call('EXPIRE', key, ttl)
            return {allowed, math.floor(tokens)}
            """;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, List.class);

    // Peek: compute current token count after refill — read-only, no HMSET.
    private static final String PEEK_SCRIPT_TEXT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_rate = tonumber(ARGV[3])
            local data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1])
            if tokens == nil then return capacity end
            local last_refill = tonumber(data[2]) or now
            local elapsed = (now - last_refill) / 1000.0
            tokens = math.min(capacity, tokens + elapsed * refill_rate)
            return math.floor(tokens)
            """;

    private static final DefaultRedisScript<Long> PEEK_SCRIPT = new DefaultRedisScript<>(PEEK_SCRIPT_TEXT, Long.class);

    private final StringRedisTemplate redis;
    private final int capacity;
    private final double refillRate; // tokens per second

    public TokenBucketRateLimiter(StringRedisTemplate redis, int capacity, int windowSeconds) {
        this.redis = redis;
        this.capacity = capacity;
        this.refillRate = (double) capacity / windowSeconds;
    }

    @Override
    public RateLimitResult check(String userId, String action) {
        String key = "token_bucket:" + userId + ":" + action;
        long nowMillis = Instant.now().toEpochMilli();

        @SuppressWarnings("unchecked")
        List<Long> result = (List<Long>) redis.execute(
                SCRIPT,
                List.of(key),
                String.valueOf(nowMillis),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(60)
        );

        boolean allowed = result != null && result.get(0) == 1L;
        long remaining = (result != null) ? result.get(1) : 0L;

        return new RateLimitResult(allowed, (int) remaining, capacity);
    }

    @Override
    public int remaining(String userId, String action) {
        String key = "token_bucket:" + userId + ":" + action;
        long nowMillis = Instant.now().toEpochMilli();

        Long result = redis.execute(PEEK_SCRIPT, List.of(key),
                String.valueOf(nowMillis), String.valueOf(capacity), String.valueOf(refillRate));
        return result != null ? (int) Math.max(0, result) : capacity;
    }
}
