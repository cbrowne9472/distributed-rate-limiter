package com.ratelimiter.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "rate_limit_rules",
    indexes = {
        @Index(name = "idx_tier_action",    columnList = "tier, action"),
        @Index(name = "idx_user_id_action", columnList = "user_id, action")
    }
)
@Data
@NoArgsConstructor
public class RateLimitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null means this rule applies to every user in the tier (tier-wide default). */
    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserTier tier;

    /** Null means the rule applies to every action for this tier/user. */
    @Column(length = 255)
    private String action;

    @Column(name = "request_limit", nullable = false)
    private int requestLimit;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;

    /** "sliding_window" or "token_bucket" — must match RateLimiterFactory keys. */
    @Column(name = "algorithm_type", nullable = false, length = 50)
    private String algorithmType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Convenience constructor for creating new tier-wide or action-specific rules. */
    public RateLimitRule(UserTier tier, String action, int requestLimit, int windowSeconds, String algorithmType) {
        this.tier = tier;
        this.action = action;
        this.requestLimit = requestLimit;
        this.windowSeconds = windowSeconds;
        this.algorithmType = algorithmType;
    }
}
