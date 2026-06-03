package com.ratelimiter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RuleRequest(
        String userId,           // null = applies to every user in the tier
        @NotBlank String tier,
        String action,           // null = applies to every action
        @Min(1) int requestLimit,
        @Min(1) int windowSeconds,
        @NotBlank String algorithmType
) {}
