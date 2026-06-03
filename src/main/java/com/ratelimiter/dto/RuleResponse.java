package com.ratelimiter.dto;

import com.ratelimiter.model.RateLimitRule;

public record RuleResponse(
        Long id,
        String userId,
        String tier,
        String action,
        int requestLimit,
        int windowSeconds,
        String algorithmType
) {
    public static RuleResponse from(RateLimitRule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getUserId(),
                rule.getTier().name(),
                rule.getAction(),
                rule.getRequestLimit(),
                rule.getWindowSeconds(),
                rule.getAlgorithmType()
        );
    }
}
