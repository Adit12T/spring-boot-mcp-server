package com.example.mcp.agents.edi;

import com.example.mcp.agents.edi.Model.Client;
import com.example.mcp.agents.edi.Model.ClientResult;
import com.example.mcp.agents.edi.Model.Finding;
import com.example.mcp.agents.edi.Model.FindingType;
import com.example.mcp.agents.edi.Model.MemberRecord;
import com.example.mcp.agents.edi.Model.Report;
import com.example.mcp.agents.edi.Model.SkippedClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares one line of business across two environments.
 * Only clients that are enabled in both environments are compared, so every comparison is like-for-like.
 */
public class Reconciler {

    private final ClientRegistry registry = new ClientRegistry();
    private final EdiStore store = new EdiStore();

    public Report reconcile(Path referenceDir, Path candidateDir, String lineOfBusiness) throws IOException {
        String referenceName = referenceDir.getFileName().toString();
        String candidateName = candidateDir.getFileName().toString();
        CauseHints hints = new CauseHints(referenceName, candidateName);

        Map<String, Client> referenceClients = registry.load(referenceDir);
        Map<String, Client> candidateClients = registry.load(candidateDir);
        Set<String> allIds = new LinkedHashSet<>(referenceClients.keySet());
        allIds.addAll(candidateClients.keySet());

        List<ClientResult> results = new ArrayList<>();
        List<SkippedClient> skipped = new ArrayList<>();
        for (String id : allIds) {
            Client inReference = referenceClients.get(id);
            Client inCandidate = candidateClients.get(id);
            boolean enabledInReference = inReference != null && inReference.enabled();
            boolean enabledInCandidate = inCandidate != null && inCandidate.enabled();

            if (!enabledInReference && !enabledInCandidate) {
                continue;
            }
            if (!enabledInReference || !enabledInCandidate) {
                skipped.add(new SkippedClient(id, "enabled only in "
                        + (enabledInReference ? referenceName : candidateName)));
                continue;
            }
            if (!inReference.linesOfBusiness().contains(lineOfBusiness)
                    || !inCandidate.linesOfBusiness().contains(lineOfBusiness)) {
                skipped.add(new SkippedClient(id, "'" + lineOfBusiness + "' is not configured in both environments"));
                continue;
            }
            results.add(compareClient(inReference, store.load(referenceDir, id, lineOfBusiness),
                    store.load(candidateDir, id, lineOfBusiness), referenceName, candidateName, hints));
        }
        return new Report(referenceName, candidateName, lineOfBusiness, results, skipped);
    }

    private ClientResult compareClient(Client client, EdiStore.Loaded reference, EdiStore.Loaded candidate,
                                       String referenceName, String candidateName, CauseHints hints) {
        List<Finding> findings = new ArrayList<>();
        String id = client.id();

        if (candidate.fileCount() == 0 && !reference.members().isEmpty()) {
            findings.add(new Finding(FindingType.NO_FILES, id, "(all)",
                    candidateName + " has no files; " + reference.members().size() + " member(s) in "
                            + referenceName + " could not be compared", hints.noFiles()));
            return new ClientResult(client, reference.members().size(), 0, findings);
        }

        Map<String, List<MemberRecord>> referenceById = byMemberId(reference.members());
        Map<String, List<MemberRecord>> candidateById = byMemberId(candidate.members());

        addDuplicates(findings, id, referenceName, referenceById, hints);
        addDuplicates(findings, id, candidateName, candidateById, hints);

        for (Map.Entry<String, List<MemberRecord>> entry : referenceById.entrySet()) {
            String memberId = entry.getKey();
            List<MemberRecord> inCandidate = candidateById.get(memberId);
            if (inCandidate == null) {
                findings.add(new Finding(FindingType.MISSING, id, memberId,
                        "in " + referenceName + " (" + entry.getValue().get(0).file() + ") but not in " + candidateName,
                        hints.missing()));
                continue;
            }
            List<String> differences = differences(entry.getValue().get(0), inCandidate.get(0),
                    referenceName, candidateName);
            if (!differences.isEmpty()) {
                Set<String> segmentIds = new TreeSet<>();
                differences.forEach(difference -> segmentIds.add(difference.substring(0, difference.indexOf(':'))));
                findings.add(new Finding(FindingType.CHANGED, id, memberId, String.join("; ", differences),
                        hints.changed(segmentIds)));
            }
        }
        for (Map.Entry<String, List<MemberRecord>> entry : candidateById.entrySet()) {
            if (!referenceById.containsKey(entry.getKey())) {
                findings.add(new Finding(FindingType.EXTRA, id, entry.getKey(),
                        "in " + candidateName + " (" + entry.getValue().get(0).file() + ") but not in " + referenceName,
                        hints.extra()));
            }
        }
        return new ClientResult(client, reference.members().size(), candidate.members().size(), findings);
    }

    private void addDuplicates(List<Finding> findings, String clientId, String environmentName,
                               Map<String, List<MemberRecord>> byId, CauseHints hints) {
        for (Map.Entry<String, List<MemberRecord>> entry : byId.entrySet()) {
            List<MemberRecord> copies = entry.getValue();
            if (copies.size() < 2) {
                continue;
            }
            boolean identical = copies.stream().allMatch(copy -> copy.segments().equals(copies.get(0).segments()));
            findings.add(new Finding(FindingType.DUPLICATE, clientId, entry.getKey(),
                    "appears " + copies.size() + " times in " + environmentName
                            + (identical ? " (identical copies)" : " (copies differ)"),
                    hints.duplicate(identical)));
        }
    }

    /** Segment-by-segment comparison of two member loops, keyed by segment id (INS, NM1, DTP, ...). */
    private List<String> differences(MemberRecord reference, MemberRecord candidate,
                                     String referenceName, String candidateName) {
        Map<String, String> referenceSegments = bySegmentId(reference);
        Map<String, String> candidateSegments = bySegmentId(candidate);
        Set<String> ids = new LinkedHashSet<>(referenceSegments.keySet());
        ids.addAll(candidateSegments.keySet());

        List<String> differences = new ArrayList<>();
        for (String segmentId : ids) {
            String left = referenceSegments.getOrDefault(segmentId, "(absent)");
            String right = candidateSegments.getOrDefault(segmentId, "(absent)");
            if (!left.equals(right)) {
                differences.add(segmentId + ": " + referenceName + " `" + left + "` vs "
                        + candidateName + " `" + right + "`");
            }
        }
        return differences;
    }

    private Map<String, String> bySegmentId(MemberRecord record) {
        Map<String, String> segments = new LinkedHashMap<>();
        for (String segment : record.segments()) {
            String segmentId = segment.contains("*") ? segment.substring(0, segment.indexOf('*')) : segment;
            segments.merge(segmentId, segment, (first, next) -> first + " | " + next);
        }
        return segments;
    }

    private Map<String, List<MemberRecord>> byMemberId(List<MemberRecord> members) {
        Map<String, List<MemberRecord>> byId = new LinkedHashMap<>();
        for (MemberRecord member : members) {
            byId.computeIfAbsent(member.memberId(), key -> new ArrayList<>()).add(member);
        }
        return byId;
    }
}
