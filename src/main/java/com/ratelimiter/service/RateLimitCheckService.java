package com.ratelimiter.service;

import com.ratelimiter.algorithm.RateLimitResult;
import com.ratelimiter.algorithm.RateLimiterAlgorithm;
import com.ratelimiter.algorithm.RateLimiterFactory;
import com.ratelimiter.dto.StatsResponse;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RateLimitCheckService {

    private final RateLimitRuleService ruleService;
    private final RateLimiterFactory   factory;
    private final MeterRegistry        meterRegistry;

    public RateLimitCheckService(RateLimitRuleService ruleService,
                                 RateLimiterFactory factory,
                                 MeterRegistry meterRegistry) {
        this.ruleService   = ruleService;
        this.factory       = factory;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Core check: resolves the rate limit rule (from Redis cache or PostgreSQL),
     * selects the correct algorithm, and runs the check against Redis.
     *
     * Four metrics are recorded on every call:
     *   ratelimit.requests.allowed / .denied  — counters tagged by userId, action, tier
     *   ratelimit.redis.latency               — timer for the Redis algorithm round-trip
     *   ratelimit.check.latency               — timer for the full check including rule lookup
     */
    public RateLimitResult check(String userId, UserTier tier, String action) {
        Timer.Sample totalSample = Timer.start(meterRegistry);

        RateLimitRule rule = ruleService.findRule(userId, tier, action);
        if (rule == null) {
            return new RateLimitResult(false, 0, 0);
        }

        RateLimiterAlgorithm algorithm =
                factory.create(rule.getAlgorithmType(), rule.getRequestLimit(), rule.getWindowSeconds());

        // ── Redis round-trip timer ────────────────────────────────────────────
        Timer.Sample redisSample = Timer.start(meterRegistry);
        RateLimitResult result = algorithm.check(userId, action);
        redisSample.stop(Timer.builder("ratelimit.redis.latency")
                .description("Time spent executing the rate limit algorithm in Redis")
                .tag("algorithm", rule.getAlgorithmType())
                .tag("tier", tier.name())
                .register(meterRegistry));

        // ── Allowed / denied counters ─────────────────────────────────────────
        Counter.builder(result.allowed() ? "ratelimit.requests.allowed" : "ratelimit.requests.denied")
                .description(result.allowed()
                        ? "Requests that passed the rate limit check"
                        : "Requests that were rejected by the rate limit check")
                .tag("userId", userId)
                .tag("action", action)
                .tag("tier", tier.name())
                .register(meterRegistry)
                .increment();

        // ── Total check timer (includes rule lookup + Redis) ──────────────────
        totalSample.stop(Timer.builder("ratelimit.check.latency")
                .description("Total time for a rate limit check including rule lookup and Redis call")
                .tag("tier", tier.name())
                .tag("result", result.allowed() ? "allowed" : "denied")
                .register(meterRegistry));

        return result;
    }

    /**
     * Returns current usage stats for a user+tier+action without consuming a token.
     * Returns null when no rule is configured for the combination.
     */
    @Nullable
    public StatsResponse getStats(String userId, UserTier tier, String action) {
        RateLimitRule rule = ruleService.findRule(userId, tier, action);
        if (rule == null) return null;

        RateLimiterAlgorithm algorithm =
                factory.create(rule.getAlgorithmType(), rule.getRequestLimit(), rule.getWindowSeconds());

        int remaining = algorithm.remaining(userId, action);
        return new StatsResponse(userId, action, tier.name(), remaining,
                rule.getRequestLimit(), rule.getWindowSeconds(), rule.getAlgorithmType());
    }
}
