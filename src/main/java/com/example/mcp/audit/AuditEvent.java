package com.example.mcp.audit;

/**
 * One tool call. Arguments are stored as a hash, so the audit trail can prove which call was made
 * without keeping customer data in the log.
 */
public record AuditEvent(
    String correlationId,
    String tool,
    String subject,
    String tenantId,
    String clientId,
    String argumentsHash,
    Outcome outcome,
    long durationMs
) {
    public enum Outcome {
        /** The tool ran and returned a result. */
        SUCCESS,
        /** The tool ran and returned a handled, structured error. */
        TOOL_ERROR,
        /** The caller's token lacks the scope for this tool. */
        DENIED,
        /** The tenant exceeded its call rate for this tool. */
        RATE_LIMITED,
        /** The tool threw an unexpected exception. */
        EXCEPTION
    }
}
