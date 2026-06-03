package com.ratelimiter.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final MeterRegistry meterRegistry;

    public DashboardController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Aggregated metric totals consumed by the React dashboard.
     * Sums across all userId/action/tier tag combinations so the
     * dashboard shows service-wide traffic without tag filtering.
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        double allowed = meterRegistry.find("ratelimit.requests.allowed")
                .counters().stream().mapToDouble(Counter::count).sum();
        double denied = meterRegistry.find("ratelimit.requests.denied")
                .counters().stream().mapToDouble(Counter::count).sum();

        return Map.of(
                "totalAllowed", (long) allowed,
                "totalDenied",  (long) denied,
                "timestamp",    Instant.now().toEpochMilli()
        );
    }
}
