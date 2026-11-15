package com.example.mcp.dto;

public record OrderStatusDto(
    String orderId,
    String tenantId,
    String customerEmail,
    String status,
    String estimatedDelivery
) {}
