package com.example.mcp.agents.debug;

import com.example.mcp.agents.Json;
import com.example.mcp.agents.debug.Model.LogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads a Kibana-style export: one JSON document per line (NDJSON). */
public class LogReader {

    /**
     * Lines that are not JSON objects, or have no usable {@code @timestamp}, are skipped instead of
     * failing the whole read: real exports contain truncated and non-JSON lines.
     */
    public List<LogEntry> read(Path file) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> doc = Json.parseObject(line);
                entries.add(new LogEntry(
                        Instant.parse(text(doc, "@timestamp")),
                        text(doc, "level"),
                        text(doc, "service"),
                        text(doc, "message"),
                        doc.get("stack_trace") instanceof String trace ? trace : null));
            } catch (RuntimeException e) {
                // unreadable line: skip it and keep the rest of the file
            }
        }
        return entries;
    }

    private static String text(Map<String, Object> doc, String field) {
        return doc.get(field) instanceof String value ? value : "";
    }
}
