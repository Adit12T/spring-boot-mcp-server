package com.example.mcp.audit;

/** Thrown when a tenant exceeds its call rate for a tool. The MCP layer returns the message as a tool error. */
public class ToolRateLimitedException extends RuntimeException {

    public ToolRateLimitedException(String tool) {
        super("ERR_RATE_LIMITED: too many calls to '" + tool + "' for this tenant. "
            + "Wait one minute before calling this tool again.");
    }
}
