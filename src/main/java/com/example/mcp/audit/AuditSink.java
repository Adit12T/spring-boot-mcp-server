package com.example.mcp.audit;

/** Where audit events go. The default writes structured log lines; a real deployment would ship them to a SIEM. */
public interface AuditSink {

    void record(AuditEvent event);
}
