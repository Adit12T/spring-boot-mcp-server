package com.example.mcp.agents.edi;

import java.util.List;

/** Small immutable data types shared by the reconciliation steps. */
public final class Model {

    private Model() {}

    public record Client(String id, String name, boolean enabled, List<String> linesOfBusiness) {}

    /** One member loop from a file: the INS segment and everything up to the next INS. */
    public record MemberRecord(String memberId, List<String> segments, String file) {}

    public enum FindingType { NO_FILES, MISSING, EXTRA, DUPLICATE, CHANGED }

    public record Finding(FindingType type, String clientId, String memberId, String detail, String likelyCause) {}

    public record ClientResult(Client client, int referenceCount, int candidateCount, List<Finding> findings) {
        public boolean clean() {
            return findings.isEmpty();
        }
    }

    public record SkippedClient(String clientId, String reason) {}

    public record Report(String referenceName, String candidateName, String lineOfBusiness,
                         List<ClientResult> results, List<SkippedClient> skipped) {
        public long findingCount() {
            return results.stream().mapToLong(result -> result.findings().size()).sum();
        }
    }
}
