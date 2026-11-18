package com.example.mcp.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Rate limiting and the refund bulkhead, built from Resilience4j's core modules. (Resilience4j's
 * Spring Boot integration targets Boot 3, so the two components are wired here instead.)
 */
@Configuration
public class ResilienceConfig {

    /** Name of the per-tenant, per-tool rate limit configuration used by ToolCallAspect. */
    public static final String TOOL_RATE_LIMIT = "mcpTool";

    @Bean
    RateLimiterRegistry rateLimiterRegistry(@Value("${tool-resilience.calls-per-minute:60}") int callsPerMinute) {
        if (callsPerMinute < 1) {
            throw new IllegalArgumentException("tool-resilience.calls-per-minute must be at least 1");
        }
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();
        registry.addConfiguration(TOOL_RATE_LIMIT, RateLimiterConfig.custom()
                .limitForPeriod(callsPerMinute)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        return registry;
    }

    @Bean
    Bulkhead refundToolBulkhead(@Value("${tool-resilience.refund-max-concurrent-calls:10}") int maxConcurrentCalls,
                                @Value("${tool-resilience.refund-max-wait:500ms}") Duration maxWait) {
        return Bulkhead.of("refundToolBulkhead", BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(maxWait)
                .build());
    }
}
