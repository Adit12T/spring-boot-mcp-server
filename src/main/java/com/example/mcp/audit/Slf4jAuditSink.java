package com.example.mcp.audit;

import com.example.mcp.agents.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Writes each audit event as one JSON line to the dedicated "AUDIT" logger. */
@Component
public class Slf4jAuditSink implements AuditSink {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    @Override
    public void record(AuditEvent event) {
        AUDIT.info(toJson(event));
    }

    static String toJson(AuditEvent event) {
        return "{\"event\":\"mcp_tool_call\""
            + ",\"correlationId\":" + quote(event.correlationId())
            + ",\"tool\":" + quote(event.tool())
            + ",\"subject\":" + quote(event.subject())
            + ",\"tenantId\":" + quote(event.tenantId())
            + ",\"clientId\":" + quote(event.clientId())
            + ",\"argumentsHash\":" + quote(event.argumentsHash())
            + ",\"outcome\":" + quote(event.outcome().name())
            + ",\"durationMs\":" + event.durationMs()
            + "}";
    }

    private static String quote(String value) {
        return value == null ? "null" : Json.quote(value);
    }
}
