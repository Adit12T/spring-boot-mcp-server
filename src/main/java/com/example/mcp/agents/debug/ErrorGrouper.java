package com.example.mcp.agents.debug;

import com.example.mcp.agents.debug.Model.ErrorGroup;
import com.example.mcp.agents.debug.Model.LogEntry;
import com.example.mcp.agents.debug.Model.ParsedTrace;
import com.example.mcp.agents.debug.Model.StackFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups ERROR entries so one bug that fired many times is investigated once. */
public class ErrorGrouper {

    private final StackTraceParser parser = new StackTraceParser();
    private final String appPackage;

    public ErrorGrouper(String appPackage) {
        this.appPackage = appPackage;
    }

    public List<ErrorGroup> group(List<LogEntry> entries) {
        Map<String, ErrorGroup> groups = new LinkedHashMap<>();
        for (LogEntry entry : entries) {
            if (!"ERROR".equals(entry.level()) || entry.stackTrace() == null) {
                continue;
            }
            ParsedTrace trace = parser.parse(entry.stackTrace());
            StackFrame appFrame = firstAppFrame(trace);
            String key = fingerprint(trace, appFrame);
            ErrorGroup existing = groups.get(key);
            if (existing == null) {
                groups.put(key, new ErrorGroup(key, trace, appFrame, 1, entry.timestamp(), entry.timestamp(),
                        entry.service(), entry.message()));
            } else {
                groups.put(key, new ErrorGroup(key, existing.trace(), existing.appFrame(), existing.count() + 1,
                        existing.firstSeen(), entry.timestamp(), existing.service(), existing.sampleMessage()));
            }
        }
        List<ErrorGroup> sorted = new ArrayList<>(groups.values());
        sorted.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return sorted;
    }

    /** The first frame that belongs to our own code, skipping JDK and framework frames. */
    private StackFrame firstAppFrame(ParsedTrace trace) {
        return trace.frames().stream()
                .filter(frame -> frame.className().startsWith(appPackage))
                .findFirst()
                .orElse(null);
    }

    /** Exception type plus method, not line number, so the fingerprint survives edits to the file. */
    private String fingerprint(ParsedTrace trace, StackFrame appFrame) {
        String where = appFrame == null ? "unknown" : appFrame.className() + "." + appFrame.method();
        return trace.exceptionType() + "@" + where;
    }
}
