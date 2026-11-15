package com.example.mcp;

import com.example.mcp.dto.RefundResponseDto;
import com.example.mcp.service.PaymentDomainService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class McpServerApplicationTests {

    @Test
    void paymentServiceShouldBeIdempotent() {
        PaymentDomainService service = new PaymentDomainService();
        RefundResponseDto first = service.processRefund("tenant-1", "ORD-123456", new BigDecimal("49.99"), "idem-001");
        RefundResponseDto second = service.getExistingRefundByIdempotencyKey("tenant-1", "idem-001");

        assertTrue(first.success());
        assertEquals(first.refundId(), second.refundId());
    }
}
