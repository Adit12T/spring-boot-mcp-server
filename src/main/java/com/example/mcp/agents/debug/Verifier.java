package com.example.mcp.agents.debug;

import com.example.mcp.agents.debug.Model.ErrorGroup;
import com.example.mcp.agents.debug.Model.LogEntry;
import com.example.mcp.agents.debug.Model.Verification;

import java.util.List;

/** Re-checks the logs taken after a fix was deployed: is the same error still being logged? */
public class Verifier {

    private final ErrorGrouper grouper;

    public Verifier(ErrorGrouper grouper) {
        this.grouper = grouper;
    }

    public Verification verify(ErrorGroup group, List<LogEntry> logsAfterFix) {
        if (logsAfterFix == null) {
            return new Verification(false, 0);
        }
        int occurrences = grouper.group(logsAfterFix).stream()
                .filter(after -> after.fingerprint().equals(group.fingerprint()))
                .mapToInt(ErrorGroup::count)
                .sum();
        return new Verification(true, occurrences);
    }
}
