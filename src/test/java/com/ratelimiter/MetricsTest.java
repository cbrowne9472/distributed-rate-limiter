package com.ratelimiter;

import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.UserTier;
import com.ratelimiter.service.RateLimitCheckService;
import com.ratelimiter.service.RateLimitRuleService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class MetricsTest {

    @Autowired MockMvc              mockMvc;
    @Autowired MeterRegistry        meterRegistry;
    @Autowired RateLimitCheckService checkService;
    @Autowired RateLimitRuleService  ruleService;
    @Autowired StringRedisTemplate   redis;

    @BeforeEach
    void flushRedis() {
        redis.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void prometheusEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    void allowedCounterIncrementsForEachAllowedRequest() {
        // Unique action tag → fresh counter series, no cross-test contamination
        String action = "metrics:allowed:" + System.nanoTime();

        checkService.check("user1", UserTier.FREE, action);
        checkService.check("user1", UserTier.FREE, action);
        checkService.check("user1", UserTier.FREE, action);

        Counter counter = meterRegistry.find("ratelimit.requests.allowed")
                .tag("userId", "user1")
                .tag("action", action)
                .counter();

        assertNotNull(counter, "ratelimit.requests.allowed counter should be registered");
        assertEquals(3.0, counter.count());
    }

    @Test
    void deniedCounterIncrementsWhenRateLimited() {
        String action = "metrics:denied:" + System.nanoTime();
        ruleService.save(new RateLimitRule(UserTier.FREE, action, 1, 60, "sliding_window"));

        checkService.check("user1", UserTier.FREE, action); // allowed — bucket is now full
        checkService.check("user1", UserTier.FREE, action); // denied

        Counter denied = meterRegistry.find("ratelimit.requests.denied")
                .tag("userId", "user1")
                .tag("action", action)
                .counter();

        assertNotNull(denied, "ratelimit.requests.denied counter should be registered after a denial");
        assertEquals(1.0, denied.count());
    }

    @Test
    void checkTimerRecordsLatencyForEachRequest() {
        String action = "metrics:timer:" + System.nanoTime();

        // Capture baseline count in case earlier tests already registered this series
        long before = timerCount("ratelimit.check.latency", "tier", "FREE", "result", "allowed");

        checkService.check("user1", UserTier.FREE, action);
        checkService.check("user1", UserTier.FREE, action);

        long after = timerCount("ratelimit.check.latency", "tier", "FREE", "result", "allowed");
        assertEquals(before + 2, after);

        // Sanity-check that actual time was recorded
        Timer timer = meterRegistry.find("ratelimit.check.latency")
                .tag("tier", "FREE").tag("result", "allowed").timer();
        assertNotNull(timer);
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) >= 0);
    }

    @Test
    void prometheusOutputContainsAllCustomMetricNames() throws Exception {
        // Trigger all four metric series
        checkService.check("user1", UserTier.FREE, "api:get");

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ratelimit_requests_allowed")))
                .andExpect(content().string(containsString("ratelimit_requests_denied")))
                .andExpect(content().string(containsString("ratelimit_check_latency")))
                .andExpect(content().string(containsString("ratelimit_redis_latency")));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long timerCount(String name, String... tags) {
        Timer t = meterRegistry.find(name).tags(tags).timer();
        return t != null ? t.count() : 0L;
    }
}
