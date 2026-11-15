package com.example.mcp.mcp;

import com.example.mcp.dto.RefundResponseDto;
import com.example.mcp.service.PaymentDomainService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class ProductionPaymentMcpTools {

    private final PaymentDomainService paymentService;
    private final StringRedisTemplate redisTemplate;

    public ProductionPaymentMcpTools(PaymentDomainService paymentService, StringRedisTemplate redisTemplate) {
        this.paymentService = paymentService;
        this.redisTemplate = redisTemplate;
    }

    @PreAuthorize("hasAuthority('SCOPE_payments:refund')")
    @Bulkhead(name = "refundToolBulkhead", fallbackMethod = "refundBulkheadFallback")
    @McpTool(
        name = "issue_customer_refund",
        description = "Issues a refund for a settled order. Use ONLY when order status is DELIVERED or RETURNED."
    )
    public RefundResponseDto issueRefund(
            @McpToolParam(description = "Unique client-generated idempotency key (UUID)", required = true) String idempotencyKey,
            @McpToolParam(description = "Order ID matching pattern ORD-[0-9]{6}", required = true) String orderId,
            @McpToolParam(description = "Refund amount in USD (must be greater than 0 and up to 500.00)", required = true) BigDecimal amount) {

        // 1. Semantic Guardrail
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(new BigDecimal("500.00")) > 0) {
            return RefundResponseDto.error(
                "ERR_AMOUNT_OUT_OF_BOUNDS",
                "Automated MCP refunds must be between $0.01 and $500.00 USD.",
                "Do not retry with a different amount. Refunds above $500.00 need manual approval by a human agent."
            );
        }

        // 2. Tenant Isolation from Validated JWT
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String tenantId = jwt.getClaimAsString("tenant_id") != null ? jwt.getClaimAsString("tenant_id") : jwt.getSubject();

        // 3. Atomic Redis Idempotency Lock
        String lockKey = "mcp:idempotency:" + tenantId + ":" + idempotencyKey;
        Boolean isFirstRequest = redisTemplate.opsForValue().setIfAbsent(lockKey, "IN_PROGRESS", Duration.ofHours(24));
        if (Boolean.FALSE.equals(isFirstRequest)) {
            return paymentService.getExistingRefundByIdempotencyKey(tenantId, idempotencyKey);
        }

        return paymentService.processRefund(tenantId, orderId, amount, idempotencyKey);
    }

    public RefundResponseDto refundBulkheadFallback(String idempotencyKey, String orderId, BigDecimal amount, Throwable t) {
        return RefundResponseDto.error(
            "ERR_BACKEND_BUSY",
            "Refund subsystem is at maximum concurrency capacity.",
            "Wait 10 seconds before retrying with the same idempotencyKey."
        );
    }
}
