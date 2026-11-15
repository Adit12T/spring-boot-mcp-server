package com.example.mcp.mcp;

import com.example.mcp.dto.OrderStatusDto;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class OrderMcpComponent {

    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    @McpTool(name = "get_order_status", description = "Fetch the current status of an order by its ID for the authenticated tenant")
    public OrderStatusDto getOrderStatus(
            @McpToolParam(description = "The unique order identifier (e.g., ORD-123456)", required = true) String orderId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String tenantId = "default-tenant";
        String email = "unknown@example.com";
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            tenantId = jwt.getClaimAsString("tenant_id") != null ? jwt.getClaimAsString("tenant_id") : jwt.getSubject();
            email = jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : "user@example.com";
        }
        return new OrderStatusDto(orderId, tenantId, email, "DELIVERED", "2026-11-10");
    }

    @McpResource(uri = "order://schema/{tableName}", name = "Order Schema", description = "Database schema for order tables")
    public String getTableSchema(String tableName) {
        return "CREATE TABLE " + tableName + " (id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL);";
    }
}
