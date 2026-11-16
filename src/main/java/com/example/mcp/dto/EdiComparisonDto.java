package com.example.mcp.dto;

import java.util.List;

public record EdiComparisonDto(
    boolean success,
    String referenceEnvironment,
    String candidateEnvironment,
    String lineOfBusiness,
    int clientsCompared,
    int differencesFound,
    List<ClientComparison> clients,
    List<String> skippedClients,
    ToolErrorDto error
) implements ToolResult {
    public record ClientComparison(String clientId, int referenceMembers, int candidateMembers,
                                   List<Difference> differences) {}

    public record Difference(String type, String memberId, String detail, String likelyCause) {}

    public static EdiComparisonDto error(String errorCode, String errorMessage, String recoveryHint) {
        return new EdiComparisonDto(false, null, null, null, 0, 0, List.of(), List.of(),
            new ToolErrorDto(errorCode, errorMessage, recoveryHint));
    }
}
