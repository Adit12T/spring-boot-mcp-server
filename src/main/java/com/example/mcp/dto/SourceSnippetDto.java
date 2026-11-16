package com.example.mcp.dto;

import java.util.List;

public record SourceSnippetDto(
    boolean success,
    String className,
    Integer line,
    Integer startLine,
    List<String> lines,
    ToolErrorDto error
) implements ToolResult {
    public static SourceSnippetDto error(String errorCode, String errorMessage, String recoveryHint) {
        return new SourceSnippetDto(false, null, null, null, List.of(),
            new ToolErrorDto(errorCode, errorMessage, recoveryHint));
    }
}
