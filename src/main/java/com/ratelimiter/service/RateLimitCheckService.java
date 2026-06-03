package com.ratelimiter.service;

import com.ratelimiter.algorithm.RateLimitResult;
import com.ratelimiter.algorithm.RateLimiterAlgorithm;
import com.ratelimiter.algorithm.RateLimiterFactory;
import com.ratelimiter.dto.StatsResponse;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RateLimitCheckService {

    private final RateLimitRuleService ruleService;
    private final RateLimiterFactory factory;

    public RateLimitCheckService(RateLimitRuleService ruleService, RateLimiterFactory factory) {
        this.ruleService = ruleService;
        this.factory = factory;
    }

    /**
     * Core check: resolves the rate limit rule for this user/tier/action (from Redis cache
     * or PostgreSQL), selects the correct algorithm, and runs the check against Redis.
     *
     * Returns a deny result when no rule is configured (fail-closed).
     */
    public RateLimitResult check(String userId, UserTier tier, String action) {
        RateLimitRule rule = ruleService.findRule(userId, tier, action);

        if (rule == null) {
            return new RateLimitResult(false, 0, 0);
        }

        RateLimiterAlgorithm algorithm =
                factory.create(rule.getAlgorithmType(), rule.getRequestLimit(), rule.getWindowSeconds());

        return algorithm.check(userId, action);
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
