package com.example.mcp.audit;

import com.example.mcp.audit.AuditEvent.Outcome;
import com.example.mcp.dto.ToolResult;
import com.example.mcp.security.CallerContext;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Wraps every {@code @McpTool} call with the cross-cutting controls: a per-tenant rate limit,
 * an audit event and a metric. It runs outside method security, so calls that are denied for a
 * missing scope are audited too.
 */
@Aspect
@Component
@Order(ToolCallAspect.ORDER)
public class ToolCallAspect {

    /**
     * Just after Spring's own invocation-exposing interceptor (HIGHEST_PRECEDENCE + 1) and well before
     * method security, so denied calls still pass through here.
     */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    static final String RATE_LIMIT_CONFIG = "mcpTool";
    static final String METRIC_NAME = "mcp.tool.calls";
    private static final String CORRELATION_ID = "correlationId";

    private final AuditSink auditSink;
    private final MeterRegistry meterRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public ToolCallAspect(AuditSink auditSink, MeterRegistry meterRegistry, RateLimiterRegistry rateLimiterRegistry) {
        this.auditSink = auditSink;
        this.meterRegistry = meterRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Around("@annotation(org.springframework.ai.mcp.annotation.McpTool)")
    public Object aroundToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        McpTool mcpTool = AnnotationUtils.findAnnotation(method, McpTool.class);
        String tool = mcpTool == null || mcpTool.name().isBlank() ? method.getName() : mcpTool.name();
        CallerContext caller = CallerContext.current();
        String correlationId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        Outcome outcome = Outcome.EXCEPTION;

        MDC.put(CORRELATION_ID, correlationId);
        try {
            RateLimiter limiter = rateLimiterRegistry.rateLimiter(caller.tenantId() + ":" + tool, RATE_LIMIT_CONFIG);
            if (!limiter.acquirePermission()) {
                outcome = Outcome.RATE_LIMITED;
                throw new ToolRateLimitedException(tool);
            }
            Object result = joinPoint.proceed();
            outcome = result instanceof ToolResult toolResult && !toolResult.success()
                ? Outcome.TOOL_ERROR
                : Outcome.SUCCESS;
            return result;
        } catch (AccessDeniedException e) {
            outcome = Outcome.DENIED;
            throw e;
        } finally {
            long durationNanos = System.nanoTime() - startedAt;
            Timer.builder(METRIC_NAME)
                .description("MCP tool calls by tool and outcome")
                .tag("tool", tool)
                .tag("outcome", outcome.name())
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
            auditSink.record(new AuditEvent(correlationId, tool, caller.subject(), caller.tenantId(),
                caller.clientId(), hash(joinPoint.getArgs()), outcome, TimeUnit.NANOSECONDS.toMillis(durationNanos)));
            MDC.remove(CORRELATION_ID);
        }
    }

    /** A short SHA-256 fingerprint of the arguments: enough to correlate calls, without storing the values. */
    static String hash(Object[] arguments) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(Arrays.deepToString(arguments).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
