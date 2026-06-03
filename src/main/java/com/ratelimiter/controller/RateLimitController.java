package com.ratelimiter.controller;

import com.ratelimiter.algorithm.RateLimitResult;
import com.ratelimiter.dto.*;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import com.ratelimiter.service.RateLimitCheckService;
import com.ratelimiter.service.RateLimitRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RateLimitController {

    private final RateLimitCheckService checkService;
    private final RateLimitRuleService  ruleService;

    public RateLimitController(RateLimitCheckService checkService, RateLimitRuleService ruleService) {
        this.checkService = checkService;
        this.ruleService  = ruleService;
    }

    /**
     * Evaluate whether a request should be allowed.
     * Returns 200 when allowed, 429 when the limit is exceeded.
     */
    @PostMapping("/check")
    public ResponseEntity<CheckResponse> check(@Valid @RequestBody CheckRequest request) {
        UserTier tier = parseTier(request.tier());
        RateLimitResult result = checkService.check(request.userId(), tier, request.action());

        CheckResponse body = new CheckResponse(
                result.allowed(), result.remaining(), result.limit(),
                request.userId(), request.action());

        return result.allowed()
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    /**
     * Create or overwrite a rate limit rule.
     * Omit userId to create a tier-wide rule; omit action to apply it to every action.
     */
    @PostMapping("/rules")
    public ResponseEntity<RuleResponse> createRule(@Valid @RequestBody RuleRequest request) {
        RateLimitRule rule = new RateLimitRule(
                parseTier(request.tier()),
                request.action(),
                request.requestLimit(),
                request.windowSeconds(),
                request.algorithmType());
        rule.setUserId(request.userId());

        return ResponseEntity.status(HttpStatus.CREATED).body(RuleResponse.from(ruleService.save(rule)));
    }

    /**
     * Return current usage stats for a user without consuming a token.
     * Query params: action, tier
     */
    @GetMapping("/stats/{userId}")
    public ResponseEntity<StatsResponse> stats(
            @PathVariable String userId,
            @RequestParam String action,
            @RequestParam String tier) {

        StatsResponse stats = checkService.getStats(userId, parseTier(tier), action);
        return stats != null ? ResponseEntity.ok(stats) : ResponseEntity.notFound().build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserTier parseTier(String tier) {
        try {
            return UserTier.valueOf(tier.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown tier: " + tier);
        }
    }
}
