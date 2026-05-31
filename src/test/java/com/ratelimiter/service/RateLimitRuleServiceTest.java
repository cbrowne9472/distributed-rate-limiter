package com.ratelimiter.service;

import com.ratelimiter.TestcontainersConfiguration;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional  // each test rolls back — Flyway seed data is always present at test start
class RateLimitRuleServiceTest {

    @Autowired
    RateLimitRuleService ruleService;

    @Test
    void seedDataContainsThreeDefaultTierRules() {
        assertEquals(3, ruleService.findAll().size());
    }

    @Test
    void freeTierGetsHundredRequestsPerMinuteByDefault() {
        Optional<RateLimitRule> rule = ruleService.findRule("user1", UserTier.FREE, "api:get");

        assertTrue(rule.isPresent());
        assertEquals(100, rule.get().getRequestLimit());
        assertEquals(60,  rule.get().getWindowSeconds());
        assertEquals("sliding_window", rule.get().getAlgorithmType());
    }

    @Test
    void proTierGetsThousandRequestsPerMinuteByDefault() {
        Optional<RateLimitRule> rule = ruleService.findRule("user1", UserTier.PRO, "api:get");

        assertTrue(rule.isPresent());
        assertEquals(1000, rule.get().getRequestLimit());
    }

    @Test
    void internalTierIsEffectivelyUnlimited() {
        Optional<RateLimitRule> rule = ruleService.findRule("svc1", UserTier.INTERNAL, "api:batch");

        assertTrue(rule.isPresent());
        assertEquals(Integer.MAX_VALUE, rule.get().getRequestLimit());
    }

    @Test
    void actionSpecificRuleTakesPriorityOverTierWideDefault() {
        // PRO users normally get 1000/min — add a tighter limit for expensive export action
        ruleService.save(new RateLimitRule(UserTier.PRO, "api:export", 10, 3600, "token_bucket"));

        Optional<RateLimitRule> exportRule = ruleService.findRule("user1", UserTier.PRO, "api:export");
        assertTrue(exportRule.isPresent());
        assertEquals(10, exportRule.get().getRequestLimit());
        assertEquals("token_bucket", exportRule.get().getAlgorithmType());

        // Other actions for the same PRO user still fall back to the tier-wide default
        Optional<RateLimitRule> defaultRule = ruleService.findRule("user1", UserTier.PRO, "api:list");
        assertTrue(defaultRule.isPresent());
        assertEquals(1000, defaultRule.get().getRequestLimit());
    }

    @Test
    void userSpecificRuleOverridesTierWideDefault() {
        // Give a specific FREE user a custom higher limit
        RateLimitRule override = new RateLimitRule(UserTier.FREE, "api:get", 500, 60, "sliding_window");
        override.setUserId("vip-user");
        ruleService.save(override);

        Optional<RateLimitRule> vipRule = ruleService.findRule("vip-user", UserTier.FREE, "api:get");
        assertTrue(vipRule.isPresent());
        assertEquals(500, vipRule.get().getRequestLimit());

        // A regular FREE user is unaffected
        Optional<RateLimitRule> normalRule = ruleService.findRule("normal-user", UserTier.FREE, "api:get");
        assertTrue(normalRule.isPresent());
        assertEquals(100, normalRule.get().getRequestLimit());
    }
}
