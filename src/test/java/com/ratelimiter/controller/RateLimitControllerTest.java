package com.ratelimiter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.TestcontainersConfiguration;
import com.ratelimiter.dto.CheckRequest;
import com.ratelimiter.dto.RuleRequest;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import com.ratelimiter.service.RateLimitRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class RateLimitControllerTest {

    @Autowired MockMvc          mockMvc;
    @Autowired ObjectMapper     objectMapper;
    @Autowired StringRedisTemplate redis;
    @Autowired RateLimitRuleService ruleService;

    @BeforeEach
    void flushRedis() {
        redis.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void checkReturns200WhenAllowed() throws Exception {
        mockMvc.perform(post("/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckRequest("user1", "FREE", "api:get"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.remaining").value(99));
    }

    @Test
    void checkReturns429WhenLimitExceeded() throws Exception {
        // Seed a tiny rule (limit = 2) so we can exhaust it in 3 calls
        RateLimitRule tinyRule = new RateLimitRule(UserTier.FREE, "api:tiny", 2, 60, "sliding_window");
        ruleService.save(tinyRule);

        CheckRequest req = new CheckRequest("user1", "FREE", "api:tiny");
        mockMvc.perform(post("/check").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/check").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/check").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void createRuleReturns201WithPersistedData() throws Exception {
        RuleRequest req = new RuleRequest(null, "PRO", "api:export", 10, 3600, "token_bucket");

        mockMvc.perform(post("/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tier").value("PRO"))
                .andExpect(jsonPath("$.action").value("api:export"))
                .andExpect(jsonPath("$.requestLimit").value(10))
                .andExpect(jsonPath("$.algorithmType").value("token_bucket"));
    }

    @Test
    void statsReturnsCurrentUsageWithoutConsumingToken() throws Exception {
        // Make one request so there is some usage to report
        mockMvc.perform(post("/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckRequest("user1", "FREE", "api:get"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/stats/user1")
                        .param("action", "api:get")
                        .param("tier", "FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user1"))
                .andExpect(jsonPath("$.remaining").value(99))   // peek — same as after the one check
                .andExpect(jsonPath("$.limit").value(100));

        // Calling stats again must not change the remaining count
        mockMvc.perform(get("/stats/user1")
                        .param("action", "api:get")
                        .param("tier", "FREE"))
                .andExpect(jsonPath("$.remaining").value(99));
    }

    @Test
    void checkReturns400ForMissingFields() throws Exception {
        mockMvc.perform(post("/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
