package com.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckRequest(
        @NotBlank String userId,
        @NotBlank String tier,
        @NotBlank String action
) {}
