package com.example.mcp.dto;

import java.math.BigDecimal;

public record RefundResponseDto(
    boolean success,
    String refundId,
    String orderId,
    BigDecimal refundedAmount,
    String errorCode,
    String errorMessage,
    String recoveryHint
) {
    public static RefundResponseDto ok(String refundId, String orderId, BigDecimal amount) {
        return new RefundResponseDto(true, refundId, orderId, amount, null, null, null);
    }

    public static RefundResponseDto error(String errorCode, String errorMessage, String recoveryHint) {
        return new RefundResponseDto(false, null, null, null, errorCode, errorMessage, recoveryHint);
    }
}
