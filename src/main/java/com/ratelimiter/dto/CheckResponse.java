package com.ratelimiter.dto;

public record CheckResponse(
        boolean allowed,
        int remaining,
        int limit,
        String userId,
        String action
) {}
