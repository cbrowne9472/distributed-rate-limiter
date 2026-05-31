package com.ratelimiter.repository;

import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule, Long> {

    /** Exact user + action match — highest-priority user-specific override. */
    Optional<RateLimitRule> findByUserIdAndAction(String userId, String action);

    /** Tier + action match with no user override (tier-action rule). */
    Optional<RateLimitRule> findByTierAndActionAndUserIdIsNull(UserTier tier, String action);

    /** Tier-wide fallback — no user, no action constraint. */
    Optional<RateLimitRule> findByTierAndUserIdIsNullAndActionIsNull(UserTier tier);

    /** All rules for a given tier (used by the rules management API). */
    List<RateLimitRule> findByTier(UserTier tier);
}
