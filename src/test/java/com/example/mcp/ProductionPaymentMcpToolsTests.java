package com.example.mcp;

import com.example.mcp.dto.RefundResponseDto;
import com.example.mcp.mcp.ProductionPaymentMcpTools;
import com.example.mcp.service.PaymentDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Refund guardrails and the idempotency lock, with Redis mocked. */
class ProductionPaymentMcpToolsTests {

    private static final String KEY = "3f1c9a52-7d0e-4b4e-9a55-0c1d2e3f4a5b";
    private static final String LOCK = "mcp:idempotency:tenant-1:" + KEY;

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ProductionPaymentMcpTools tools = new ProductionPaymentMcpTools(new PaymentDomainService(), redis);

    @BeforeEach
    void authenticateAsTenantOne() {
        when(redis.opsForValue()).thenReturn(values);
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("agent-1")
            .claim("tenant_id", "tenant-1")
            .build();
        // Passing authorities marks the token as authenticated, as the resource server does for a valid JWT.
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_payments:refund"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInvalidInputBeforeTouchingRedis() {
        assertEquals("ERR_AMOUNT_OUT_OF_BOUNDS",
            tools.issueRefund(KEY, "ORD-123456", new BigDecimal("500.01")).errorCode());
        assertEquals("ERR_INVALID_ORDER_ID",
            tools.issueRefund(KEY, "123456", new BigDecimal("10.00")).errorCode());
        assertEquals("ERR_INVALID_IDEMPOTENCY_KEY",
            tools.issueRefund("x", "ORD-123456", new BigDecimal("10.00")).errorCode());

        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void firstRequestIssuesTheRefundAndMarksTheLockCompleted() {
        when(values.setIfAbsent(eq(LOCK), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true);

        RefundResponseDto result = tools.issueRefund(KEY, "ORD-123456", new BigDecimal("49.99"));

        assertTrue(result.success());
        verify(values).set(eq(LOCK), startsWith("COMPLETED:REF-"), any(Duration.class));
    }

    @Test
    void aRefundThatWentThroughIsStillReportedWhenTheLockCannotBeMarkedCompleted() {
        when(values.setIfAbsent(eq(LOCK), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("redis unavailable"))
            .when(values).set(eq(LOCK), anyString(), any(Duration.class));

        RefundResponseDto result = tools.issueRefund(KEY, "ORD-123456", new BigDecimal("49.99"));

        assertTrue(result.success());
        verify(redis, never()).delete(LOCK);
    }

    @Test
    void retryWithTheSameKeyReturnsTheOriginalRefund() {
        when(values.setIfAbsent(eq(LOCK), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true, false);

        RefundResponseDto first = tools.issueRefund(KEY, "ORD-123456", new BigDecimal("49.99"));
        RefundResponseDto retry = tools.issueRefund(KEY, "ORD-123456", new BigDecimal("49.99"));

        assertEquals(first.refundId(), retry.refundId());
    }

    @Test
    void reusingAKeyForADifferentRefundIsRejected() {
        when(values.setIfAbsent(eq(LOCK), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true, false);

        tools.issueRefund(KEY, "ORD-123456", new BigDecimal("49.99"));
        RefundResponseDto reused = tools.issueRefund(KEY, "ORD-654321", new BigDecimal("75.00"));

        assertFalse(reused.success());
        assertEquals("ERR_IDEMPOTENCY_KEY_REUSED", reused.errorCode());
    }
}
