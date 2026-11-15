package com.example.mcp.agents.debug;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Small immutable data types shared by the pipeline steps. */
public final class Model {

    private Model() {}

    public record LogEntry(Instant timestamp, String level, String service, String message, String stackTrace) {}

    public record StackFrame(String className, String method, String file, int line) {
        public String simpleClassName() {
            String outer = className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
            return outer.substring(outer.lastIndexOf('.') + 1);
        }

        public String packageName() {
            int dot = className.lastIndexOf('.');
            return dot < 0 ? "" : className.substring(0, dot);
        }
    }

    /** The root exception of a stack trace (the last "Caused by", if there is one). */
    public record ParsedTrace(String exceptionType, String exceptionMessage, List<StackFrame> frames) {
        public String simpleType() {
            return exceptionType.substring(exceptionType.lastIndexOf('.') + 1);
        }
    }

    /** All log entries that share the same root exception at the same application method. */
    public record ErrorGroup(String fingerprint, ParsedTrace trace, StackFrame appFrame, int count,
                             Instant firstSeen, Instant lastSeen, String service, String sampleMessage) {}

    public record SourceSnippet(Path file, int line, int startLine, List<String> lines) {
        public String failingLine() {
            int index = line - startLine;
            return index >= 0 && index < lines.size() ? lines.get(index) : "";
        }
    }

    public record Diagnosis(String rootCause, String suggestedFix, String regressionTest, String analyst) {}

    public record Verification(boolean checked, int occurrencesAfter) {
        public boolean resolved() {
            return checked && occurrencesAfter == 0;
        }
    }

    public record Finding(ErrorGroup group, SourceSnippet snippet, Diagnosis diagnosis, Verification verification) {}
}
