package com.example.mcp.agents.debug;

import com.example.mcp.agents.debug.Model.ParsedTrace;
import com.example.mcp.agents.debug.Model.StackFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a Java stack trace into its root exception and frames. */
public class StackTraceParser {

    private static final String CAUSED_BY = "Caused by: ";

    // e.g. "at java.base/java.lang.Thread.run(Thread.java:1583)"
    private static final Pattern FRAME = Pattern.compile(
            "^\\s*at\\s+(?:[\\w.]+/)?([\\w.$]+)\\.([\\w$<>]+)\\(([\\w$]+\\.java):(\\d+)\\)\\s*$");

    public ParsedTrace parse(String stackTrace) {
        String[] lines = stackTrace.split("\\R");

        // The root cause is the last "Caused by" section; without one it is the first line.
        int start = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(CAUSED_BY)) {
                start = i;
            }
        }

        String header = lines[start].startsWith(CAUSED_BY) ? lines[start].substring(CAUSED_BY.length()) : lines[start];
        int colon = header.indexOf(": ");
        String type = colon < 0 ? header.trim() : header.substring(0, colon).trim();
        String message = colon < 0 ? "" : header.substring(colon + 2).trim();

        List<StackFrame> frames = new ArrayList<>();
        for (int i = start + 1; i < lines.length; i++) {
            Matcher m = FRAME.matcher(lines[i]);
            if (m.matches()) {
                frames.add(new StackFrame(m.group(1), m.group(2), m.group(3), Integer.parseInt(m.group(4))));
            }
        }
        return new ParsedTrace(type, message, List.copyOf(frames));
    }
}
