package com.example.mcp.agents.edi;

import com.example.mcp.agents.edi.Model.MemberRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a simplified X12-style enrollment file: segments end with "~", elements are separated by "*".
 * Each member loop starts at an INS segment; the member id is the REF*0F value inside the loop.
 */
public class EdiParser {

    public List<MemberRecord> parse(Path file) throws IOException {
        List<MemberRecord> members = new ArrayList<>();
        List<String> current = null;

        for (String raw : Files.readString(file).split("~")) {
            String segment = raw.strip();
            if (segment.isEmpty()) {
                continue;
            }
            String id = segment.contains("*") ? segment.substring(0, segment.indexOf('*')) : segment;
            if (id.equals("INS")) {
                addMember(members, current, file);
                current = new ArrayList<>();
                current.add(segment);
            } else if (id.equals("SE") || id.equals("GE") || id.equals("IEA")) {
                addMember(members, current, file);
                current = null;
            } else if (current != null) {
                current.add(segment);
            }
        }
        addMember(members, current, file);
        return members;
    }

    private void addMember(List<MemberRecord> members, List<String> segments, Path file) {
        if (segments == null) {
            return;
        }
        String memberId = segments.stream()
                .filter(segment -> segment.startsWith("REF*0F*"))
                .map(segment -> segment.substring("REF*0F*".length()))
                .findFirst()
                .orElse("(no member id, loop " + (members.size() + 1) + ")");
        members.add(new MemberRecord(memberId, List.copyOf(segments), file.getFileName().toString()));
    }
}
