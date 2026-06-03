package com.ratelimiter.dto;

public record StatsResponse(
        String userId,
        String action,
        String tier,
        int remaining,
        int limit,
        int windowSeconds,
        String algorithmType
) {}
