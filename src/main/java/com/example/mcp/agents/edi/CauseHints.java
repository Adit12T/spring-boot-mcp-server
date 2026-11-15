package com.example.mcp.agents.edi;

import java.util.Set;

/**
 * Likely causes for each kind of difference. These are starting points for a developer, not conclusions.
 * They are rule-based on purpose: an agent can explain these results, but the findings themselves come from code.
 */
public class CauseHints {

    private final String reference;
    private final String candidate;

    public CauseHints(String reference, String candidate) {
        this.reference = reference;
        this.candidate = candidate;
    }

    public String noFiles() {
        return "No files were produced or delivered in " + candidate
                + ". Check that the export job ran and the file transfer completed.";
    }

    public String missing() {
        return "The record was filtered out or rejected in " + candidate
                + ". Check eligibility and mapping rules, and the load log for validation errors.";
    }

    public String extra() {
        return "The record exists only in " + candidate + ". Likely a stale record that was not purged, "
                + "or a load that has not run in " + reference + ".";
    }

    public String duplicate(boolean identicalCopies) {
        return identicalCopies
                ? "The same record was written twice. Likely a file processed twice or a re-run without de-duplication."
                : "Two versions of the record exist. Check effective-date handling and whether an update "
                + "was appended instead of replacing the earlier record.";
    }

    public String changed(Set<String> differingSegmentIds) {
        if (differingSegmentIds.equals(Set.of("DTP"))) {
            return "Only dates differ. Check date mapping, time zone and cut-off rules between the environments.";
        }
        return "Segment " + String.join(", ", differingSegmentIds)
                + " differs. Check the mapping rule that populates it in each environment.";
    }
}
