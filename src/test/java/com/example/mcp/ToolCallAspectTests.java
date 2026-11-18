package com.example.mcp;

import com.example.mcp.audit.AuditEvent;
import com.example.mcp.audit.AuditEvent.Outcome;
import com.example.mcp.audit.ToolCallAspect;
import com.example.mcp.audit.ToolRateLimitedException;
import com.example.mcp.dto.SourceSnippetDto;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises the audit, metric and rate-limit wrapper on a small sample tool, without starting Spring. */
class ToolCallAspectTests {

    public static class SampleTools {

        @McpTool(name = "echo", description = "Returns its input")
        public String echo(String secret) {
            return secret;
        }

        @McpTool(name = "handled_error", description = "Returns a structured error")
        public SourceSnippetDto handledError() {
            return SourceSnippetDto.error("ERR_TEST", "handled", "none");
        }

        @McpTool(name = "boom", description = "Throws")
        public String boom() {
            throw new IllegalStateException("boom");
        }
    }

    private final List<AuditEvent> audit = new ArrayList<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private SampleTools tools;

    @BeforeEach
    void setUp() {
        RateLimiterConfig twoPerMinute = RateLimiterConfig.custom()
            .limitForPeriod(2)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();
        ToolCallAspect aspect = new ToolCallAspect(audit::add, meters,
            RateLimiterRegistry.of(Map.of("mcpTool", twoPerMinute)));

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleTools());
        factory.addAspect(aspect);
        tools = factory.getProxy();
    }

    @Test
    void auditsASuccessfulCallWithoutStoringItsArguments() {
        assertEquals("card 4111", tools.echo("card 4111"));

        assertEquals(1, audit.size());
        AuditEvent event = audit.get(0);
        assertEquals("echo", event.tool());
        assertEquals(Outcome.SUCCESS, event.outcome());
        assertEquals(16, event.argumentsHash().length());
        assertFalse(event.toString().contains("4111"));
        assertEquals(1, meters.get("mcp.tool.calls").tag("tool", "echo").tag("outcome", "SUCCESS").timer().count());
    }

    @Test
    void recordsHandledErrorsAndExceptionsAsDifferentOutcomes() {
        tools.handledError();
        assertThrows(IllegalStateException.class, () -> tools.boom());

        assertEquals(Outcome.TOOL_ERROR, audit.get(0).outcome());
        assertEquals(Outcome.EXCEPTION, audit.get(1).outcome());
    }

    @Test
    void rateLimitsPerTenantAndTool() {
        tools.echo("a");
        tools.echo("b");
        ToolRateLimitedException rejected = assertThrows(ToolRateLimitedException.class, () -> tools.echo("c"));

        assertEquals(true, rejected.getMessage().startsWith("ERR_RATE_LIMITED"));
        assertEquals(Outcome.RATE_LIMITED, audit.get(2).outcome());
        // A different tool has its own budget.
        tools.handledError();
        assertEquals(Outcome.TOOL_ERROR, audit.get(3).outcome());
    }
}
