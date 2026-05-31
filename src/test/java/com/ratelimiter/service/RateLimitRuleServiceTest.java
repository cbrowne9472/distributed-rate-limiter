package com.ratelimiter.service;

import com.ratelimiter.TestcontainersConfiguration;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

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
        RateLimitRule rule = ruleService.findRule("user1", UserTier.FREE, "api:get");

        assertNotNull(rule);
        assertEquals(100, rule.getRequestLimit());
        assertEquals(60,  rule.getWindowSeconds());
        assertEquals("sliding_window", rule.getAlgorithmType());
    }

    @Test
    void proTierGetsThousandRequestsPerMinuteByDefault() {
        RateLimitRule rule = ruleService.findRule("user1", UserTier.PRO, "api:get");

        assertNotNull(rule);
        assertEquals(1000, rule.getRequestLimit());
    }

    @Test
    void internalTierIsEffectivelyUnlimited() {
        RateLimitRule rule = ruleService.findRule("svc1", UserTier.INTERNAL, "api:batch");

        assertNotNull(rule);
        assertEquals(Integer.MAX_VALUE, rule.getRequestLimit());
    }

    @Test
    void actionSpecificRuleTakesPriorityOverTierWideDefault() {
        ruleService.save(new RateLimitRule(UserTier.PRO, "api:export", 10, 3600, "token_bucket"));

        RateLimitRule exportRule = ruleService.findRule("user1", UserTier.PRO, "api:export");
        assertNotNull(exportRule);
        assertEquals(10, exportRule.getRequestLimit());
        assertEquals("token_bucket", exportRule.getAlgorithmType());

        // Other PRO actions still fall back to the tier-wide default
        RateLimitRule defaultRule = ruleService.findRule("user1", UserTier.PRO, "api:list");
        assertNotNull(defaultRule);
        assertEquals(1000, defaultRule.getRequestLimit());
    }

    @Test
    void userSpecificRuleOverridesTierWideDefault() {
        RateLimitRule override = new RateLimitRule(UserTier.FREE, "api:get", 500, 60, "sliding_window");
        override.setUserId("vip-user");
        ruleService.save(override);

        RateLimitRule vipRule = ruleService.findRule("vip-user", UserTier.FREE, "api:get");
        assertNotNull(vipRule);
        assertEquals(500, vipRule.getRequestLimit());

        // A regular FREE user is unaffected
        RateLimitRule normalRule = ruleService.findRule("normal-user", UserTier.FREE, "api:get");
        assertNotNull(normalRule);
        assertEquals(100, normalRule.getRequestLimit());
    }
}
