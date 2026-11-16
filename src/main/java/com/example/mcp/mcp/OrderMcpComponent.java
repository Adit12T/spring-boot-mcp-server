package com.example.mcp.mcp;

import com.example.mcp.dto.OrderStatusDto;
import com.example.mcp.security.CallerContext;
import com.example.mcp.security.PiiMasker;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class OrderMcpComponent {

    private static final Pattern TABLE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    @McpTool(
        name = "get_order_status",
        title = "Get order status",
        description = "Fetch the current status of an order by its ID for the authenticated tenant. Read-only.",
        annotations = @McpTool.McpAnnotations(
            title = "Get order status", readOnlyHint = true, destructiveHint = false,
            idempotentHint = true, openWorldHint = false)
    )
    public OrderStatusDto getOrderStatus(
            @McpToolParam(description = "The unique order identifier (e.g., ORD-123456)", required = true) String orderId) {
        CallerContext caller = CallerContext.current();
        String email = "user@example.com";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("email") != null) {
            email = jwt.getClaimAsString("email");
        }
        // The model needs to know an email is on file, not what it is.
        return new OrderStatusDto(orderId, caller.tenantId(), PiiMasker.mask(email), "DELIVERED", "2026-11-10");
    }

    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    @McpResource(uri = "order://schema/{tableName}", name = "Order Schema", description = "Database schema for order tables")
    public String getTableSchema(String tableName) {
        // The name is echoed into DDL text, so only a plain identifier is accepted.
        if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("tableName must be a lower-case identifier such as 'orders'.");
        }
        return "CREATE TABLE " + tableName + " (id VARCHAR(32) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL);";
    }
}
