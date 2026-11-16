package com.example.mcp.mcp;

import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * Reusable workflows the client can offer to users. Each prompt tells the agent which tools to call,
 * in what order, and to ground every claim in tool output.
 */
@Component
public class EngineeringMcpPrompts {

    @McpPrompt(
        name = "investigate_production_errors",
        title = "Investigate production errors",
        description = "Step-by-step workflow for diagnosing the errors currently in the service logs."
    )
    public String investigateProductionErrors(
            @McpArg(name = "focus", description = "Optional exception type or class name to concentrate on", required = false)
            String focus) {
        String scope = focus == null || focus.isBlank()
            ? "Cover every distinct error."
            : "Concentrate on errors related to: " + focus.strip() + ".";
        return """
            Investigate the errors in the service logs. %s

            1. Call analyze_error_logs once. Do not guess at errors that are not in its output.
            2. For each finding, if the failing code is not enough to judge the fix, call get_java_source
               with the finding's className and line.
            3. For each finding report: the exception, the class and line, the root cause, the suggested fix,
               and the verification status (RESOLVED or STILL_OCCURRING).
            4. Present the regression test draft as a draft for a developer to complete. Do not claim it passes.
            5. If a tool returns an error object, report its errorCode and follow its recoveryHint. Do not retry
               unless the hint says to.

            Only state facts that appear in tool output. Say "not in the logs" when something is not there.
            """.formatted(scope);
    }

    @McpPrompt(
        name = "reconcile_edi_release",
        title = "Reconcile EDI before a release",
        description = "Workflow for checking that the candidate environment produces the same EDI output as the reference."
    )
    public String reconcileEdiRelease(
            @McpArg(name = "lineOfBusiness", description = "Line of business to compare, e.g. medical", required = true)
            String lineOfBusiness) {
        return """
            Check whether the candidate environment is ready for release for the line of business "%s".

            1. Call compare_edi_environments with lineOfBusiness set to that value, in lower case.
            2. Summarise per client: members in each environment and the number of differences.
            3. List every difference with its type (MISSING, EXTRA, DUPLICATE, CHANGED, NO_FILES), the member id,
               and the likely cause exactly as returned. Likely causes are hints for a developer to confirm.
            4. List skipped clients and why they were skipped.
            5. Give a release recommendation: "ready" only if there are zero differences; otherwise "not ready"
               with the differences that block it.

            Use only counts and ids from the tool output. Do not estimate.
            """.formatted(lineOfBusiness == null ? "" : lineOfBusiness.strip());
    }
}
