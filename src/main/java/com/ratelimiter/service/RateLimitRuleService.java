package com.ratelimiter.service;

import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import com.ratelimiter.repository.RateLimitRuleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RateLimitRuleService {

    static final String CACHE_NAME = "rules";

    private final RateLimitRuleRepository repository;

    public RateLimitRuleService(RateLimitRuleRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the most specific rule for a user+tier+action combination, or null if none exists.
     * Result is cached in Redis for 5 minutes (TTL set in CacheConfig).
     *
     * Priority:  user+action  >  tier+action  >  tier-wide
     */
    @Cacheable(value = CACHE_NAME, key = "#userId + ':' + #tier.name() + ':' + #action",
               unless = "#result == null")
    @Nullable
    public RateLimitRule findRule(String userId, UserTier tier, String action) {
        if (userId != null && action != null) {
            RateLimitRule userRule = repository.findByUserIdAndAction(userId, action).orElse(null);
            if (userRule != null) return userRule;
        }

        if (action != null) {
            RateLimitRule tierActionRule =
                    repository.findByTierAndActionAndUserIdIsNull(tier, action).orElse(null);
            if (tierActionRule != null) return tierActionRule;
        }

        return repository.findByTierAndUserIdIsNullAndActionIsNull(tier).orElse(null);
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public RateLimitRule save(RateLimitRule rule) {
        return repository.save(rule);
    }

    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
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
