package com.example.mcp.agents.edi;

import com.example.mcp.agents.edi.Model.MemberRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Finds a client's EDI files for one line of business: {environment}/edi/{client}/{lineOfBusiness}/*.edi */
public class EdiStore {

    public record Loaded(int fileCount, List<MemberRecord> members) {}

    private final EdiParser parser = new EdiParser();

    public Loaded load(Path environmentDir, String clientId, String lineOfBusiness) throws IOException {
        Path dir = environmentDir.resolve("edi").resolve(clientId).resolve(lineOfBusiness);
        if (!Files.isDirectory(dir)) {
            return new Loaded(0, List.of());
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(dir)) {
            files = listing.filter(path -> path.getFileName().toString().endsWith(".edi")).sorted().toList();
        }
        List<MemberRecord> members = new ArrayList<>();
        for (Path file : files) {
            members.addAll(parser.parse(file));
        }
        return new Loaded(files.size(), members);
    }
}
