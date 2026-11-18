package com.example.mcp.mcp;

import com.example.mcp.dto.RefundResponseDto;
import com.example.mcp.security.CallerContext;
import com.example.mcp.service.PaymentDomainService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.regex.Pattern;

@Component
public class ProductionPaymentMcpTools {

    private static final Pattern ORDER_ID = Pattern.compile("ORD-[0-9]{6}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final BigDecimal MAX_AUTOMATED_REFUND = new BigDecimal("500.00");
    private static final Duration LOCK_TTL = Duration.ofHours(24);

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
        title = "Issue customer refund",
        description = "Issues a refund for a settled order. Use ONLY when order status is DELIVERED or RETURNED. "
            + "Moves money and cannot be undone. Safe to retry with the same idempotencyKey.",
        annotations = @McpTool.McpAnnotations(
            title = "Issue customer refund", readOnlyHint = false, destructiveHint = true,
            idempotentHint = true, openWorldHint = false)
    )
    public RefundResponseDto issueRefund(
            @McpToolParam(description = "Unique client-generated idempotency key (UUID)", required = true) String idempotencyKey,
            @McpToolParam(description = "Order ID matching pattern ORD-[0-9]{6}", required = true) String orderId,
            @McpToolParam(description = "Refund amount in USD (must be greater than 0 and up to 500.00)", required = true) BigDecimal amount) {

        // 1. Input guardrails: what the descriptions promise is enforced here, not left to the model.
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            return RefundResponseDto.error(
                "ERR_INVALID_IDEMPOTENCY_KEY",
                "idempotencyKey must be 8 to 64 letters, digits or hyphens, for example a UUID.",
                "Generate one UUID for this refund and reuse it on every retry."
            );
        }
        if (orderId == null || !ORDER_ID.matcher(orderId).matches()) {
            return RefundResponseDto.error(
                "ERR_INVALID_ORDER_ID",
                "orderId must match ORD- followed by six digits.",
                "Look the order up with get_order_status and use its exact orderId."
            );
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(MAX_AUTOMATED_REFUND) > 0) {
            return RefundResponseDto.error(
                "ERR_AMOUNT_OUT_OF_BOUNDS",
                "Automated MCP refunds must be between $0.01 and $500.00 USD.",
                "Do not retry with a different amount. Refunds above $500.00 need manual approval by a human agent."
            );
        }

        // 2. Tenant isolation from the validated JWT, never from tool arguments.
        String tenantId = CallerContext.current().tenantId();

        // 3. Atomic Redis idempotency lock: only the first request with this key may move money.
        String lockKey = "mcp:idempotency:" + tenantId + ":" + idempotencyKey;
        Boolean isFirstRequest = redisTemplate.opsForValue().setIfAbsent(lockKey, "IN_PROGRESS", LOCK_TTL);
        if (!Boolean.TRUE.equals(isFirstRequest)) {
            RefundResponseDto existing = paymentService.getExistingRefundByIdempotencyKey(tenantId, idempotencyKey);
            if (existing.success()
                    && (!orderId.equals(existing.orderId()) || amount.compareTo(existing.refundedAmount()) != 0)) {
                return RefundResponseDto.error(
                    "ERR_IDEMPOTENCY_KEY_REUSED",
                    "This idempotencyKey was already used for a different order or amount.",
                    "Use a new idempotencyKey only for a new refund; reuse a key only to retry the same refund."
                );
            }
            return existing;
        }

        // 4. Finish the lock's life cycle: release it on failure so a retry can proceed, mark it completed on success.
        RefundResponseDto result;
        try {
            result = paymentService.processRefund(tenantId, orderId, amount, idempotencyKey);
        } catch (RuntimeException e) {
            redisTemplate.delete(lockKey);
            return RefundResponseDto.error(
                "ERR_REFUND_FAILED",
                "The refund could not be processed. No money was moved.",
                "Retry once with the same idempotencyKey. If it fails again, hand off to a human agent."
            );
        }
        try {
            redisTemplate.opsForValue().set(lockKey, "COMPLETED:" + result.refundId(), LOCK_TTL);
        } catch (RuntimeException e) {
            // The money has moved, so the refund must still be reported as issued. The lock stays
            // IN_PROGRESS until it expires, and a retry with the same key gets this same refund back.
        }
        return result;
    }

    public RefundResponseDto refundBulkheadFallback(String idempotencyKey, String orderId, BigDecimal amount, Throwable t) {
        return RefundResponseDto.error(
            "ERR_BACKEND_BUSY",
            "Refund subsystem is at maximum concurrency capacity.",
            "Wait 10 seconds before retrying with the same idempotencyKey."
        );
    }
}
