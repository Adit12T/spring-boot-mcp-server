package com.example.mcp.agents.debug;

import com.example.mcp.agents.debug.Model.SourceSnippet;
import com.example.mcp.agents.debug.Model.StackFrame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Maps a stack frame to the source file and returns the lines around the failing one. */
public class SourceLocator {

    private static final int CONTEXT_LINES = 4;

    private final Path sourceRoot;

    public SourceLocator(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public SourceSnippet locate(StackFrame frame) throws IOException {
        if (frame == null) {
            return null;
        }
        String outerClass = frame.className().contains("$")
                ? frame.className().substring(0, frame.className().indexOf('$'))
                : frame.className();
        Path file = sourceRoot.resolve(outerClass.replace('.', '/') + ".java");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        List<String> all = Files.readAllLines(file);
        if (frame.line() < 1 || frame.line() > all.size()) {
            return null;
        }
        int start = Math.max(1, frame.line() - CONTEXT_LINES);
        int end = Math.min(all.size(), frame.line() + CONTEXT_LINES);
        return new SourceSnippet(file, frame.line(), start, List.copyOf(all.subList(start - 1, end)));
    }
}
