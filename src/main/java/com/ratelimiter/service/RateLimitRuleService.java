package com.ratelimiter.service;

import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import com.ratelimiter.repository.RateLimitRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RateLimitRuleService {

    private final RateLimitRuleRepository repository;

    public RateLimitRuleService(RateLimitRuleRepository repository) {
        this.repository = repository;
    }

    /**
     * Finds the most specific rule for a user+tier+action combination.
     *
     * Priority (highest to lowest):
     *   1. User-specific + action-specific override
     *   2. Tier-specific + action-specific rule
     *   3. Tier-wide fallback (action = null)
     */
    public Optional<RateLimitRule> findRule(String userId, UserTier tier, String action) {
        if (userId != null && action != null) {
            Optional<RateLimitRule> userRule = repository.findByUserIdAndAction(userId, action);
            if (userRule.isPresent()) return userRule;
        }

        if (action != null) {
            Optional<RateLimitRule> tierActionRule = repository.findByTierAndActionAndUserIdIsNull(tier, action);
            if (tierActionRule.isPresent()) return tierActionRule;
        }

        return repository.findByTierAndUserIdIsNullAndActionIsNull(tier);
    }

    @Transactional
    public RateLimitRule save(RateLimitRule rule) {
        return repository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<RateLimitRule> findAll() {
        return repository.findAll();
    }

    public List<RateLimitRule> findByTier(UserTier tier) {
        return repository.findByTier(tier);
    }
}
