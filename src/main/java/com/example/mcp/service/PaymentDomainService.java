package com.example.mcp.service;

import com.example.mcp.dto.RefundResponseDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentDomainService {

    private final Map<String, RefundResponseDto> completedRefunds = new ConcurrentHashMap<>();

    public RefundResponseDto processRefund(String tenantId, String orderId, BigDecimal amount, String idempotencyKey) {
        String key = tenantId + ":" + idempotencyKey;
        return completedRefunds.computeIfAbsent(key, k ->
            RefundResponseDto.ok("REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), orderId, amount)
        );
    }

    public RefundResponseDto getExistingRefundByIdempotencyKey(String tenantId, String idempotencyKey) {
        String key = tenantId + ":" + idempotencyKey;
        return completedRefunds.getOrDefault(
            key,
            RefundResponseDto.error(
                "ERR_IDEMPOTENCY_IN_FLIGHT",
                "A refund with this idempotencyKey is currently being processed.",
                "Do not generate a new idempotencyKey. Poll order status in 5 seconds."
            )
        );
    }
}
