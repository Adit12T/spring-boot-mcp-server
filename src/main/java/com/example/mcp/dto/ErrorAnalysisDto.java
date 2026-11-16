package com.example.mcp.dto;

import java.util.List;

public record ErrorAnalysisDto(
    boolean success,
    int distinctErrors,
    List<ErrorFinding> findings,
    ToolErrorDto error
) implements ToolResult {
    public record ErrorFinding(
        String exceptionType,
        String exceptionMessage,
        int occurrences,
        String className,
        String method,
        Integer line,
        String failingCode,
        String rootCause,
        String suggestedFix,
        String regressionTestDraft,
        String verification
    ) {}

    public static ErrorAnalysisDto error(String errorCode, String errorMessage, String recoveryHint) {
        return new ErrorAnalysisDto(false, 0, List.of(), new ToolErrorDto(errorCode, errorMessage, recoveryHint));
    }
}
