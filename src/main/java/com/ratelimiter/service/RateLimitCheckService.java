package com.ratelimiter.service;

import com.ratelimiter.algorithm.RateLimitResult;
import com.ratelimiter.algorithm.RateLimiterAlgorithm;
import com.ratelimiter.algorithm.RateLimiterFactory;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
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
}
