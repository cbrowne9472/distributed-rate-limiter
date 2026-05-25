package com.ratelimiter.algorithm;

public record RateLimitResult(boolean allowed, int remaining, int limit) {}
