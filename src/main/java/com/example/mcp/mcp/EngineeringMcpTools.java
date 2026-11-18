package com.example.mcp.mcp;

import com.example.mcp.agents.debug.ErrorGrouper;
import com.example.mcp.agents.debug.LogReader;
import com.example.mcp.agents.debug.Model.Diagnosis;
import com.example.mcp.agents.debug.Model.ErrorGroup;
import com.example.mcp.agents.debug.Model.LogEntry;
import com.example.mcp.agents.debug.Model.SourceSnippet;
import com.example.mcp.agents.debug.Model.StackFrame;
import com.example.mcp.agents.debug.Model.Verification;
import com.example.mcp.agents.debug.RuleBasedAnalyst;
import com.example.mcp.agents.debug.SourceLocator;
import com.example.mcp.agents.debug.Verifier;
import com.example.mcp.agents.edi.Model.ClientResult;
import com.example.mcp.agents.edi.Model.Report;
import com.example.mcp.agents.edi.Reconciler;
import com.example.mcp.dto.EdiComparisonDto;
import com.example.mcp.dto.ErrorAnalysisDto;
import com.example.mcp.dto.SourceSnippetDto;
import com.example.mcp.security.PiiMasker;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Read-only engineering tools for agents: EDI comparison across two environments,
 * error analysis from logs, and Java source lookup. Each tool needs its own OAuth scope.
 */
@Component
public class EngineeringMcpTools {

    private static final Pattern LINE_OF_BUSINESS = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    private final Path ediReferenceDir;
    private final Path ediCandidateDir;
    private final Path logsFile;
    private final Path postFixLogsFile;
    private final Path sourceRoot;
    private final String appPackage;

    public EngineeringMcpTools(
            @Value("${demo.edi.reference-dir}") String ediReferenceDir,
            @Value("${demo.edi.candidate-dir}") String ediCandidateDir,
            @Value("${demo.logs.file}") String logsFile,
            @Value("${demo.logs.post-fix-file}") String postFixLogsFile,
            @Value("${demo.source.root}") String sourceRoot,
            @Value("${demo.source.app-package}") String appPackage) {
        this.ediReferenceDir = Path.of(ediReferenceDir);
        this.ediCandidateDir = Path.of(ediCandidateDir);
        this.logsFile = Path.of(logsFile);
        this.postFixLogsFile = Path.of(postFixLogsFile);
        this.sourceRoot = Path.of(sourceRoot);
        this.appPackage = appPackage;
    }

    @PreAuthorize("hasAuthority('SCOPE_edi:read')")
    @McpTool(
        name = "compare_edi_environments",
        title = "Compare EDI environments",
        annotations = @McpTool.McpAnnotations(
            title = "Compare EDI environments", readOnlyHint = true, destructiveHint = false,
            idempotentHint = true, openWorldHint = false),
        description = "Compares EDI files between the reference and candidate environments for one line of business. "
            + "Only clients enabled in both environments are compared. Returns missing, extra, duplicate and "
            + "changed members with a likely cause for each. Read-only."
    )
    public EdiComparisonDto compareEdiEnvironments(
            @McpToolParam(description = "Line of business in lower case, e.g. medical or dental", required = true)
            String lineOfBusiness) {

        // Guardrail: the value becomes part of a file path, so only a plain lower-case name is accepted.
        if (lineOfBusiness == null || !LINE_OF_BUSINESS.matcher(lineOfBusiness).matches()) {
            return EdiComparisonDto.error(
                "ERR_INVALID_LINE_OF_BUSINESS",
                "lineOfBusiness must be a lower-case name such as 'medical'.",
                "Retry with a plain lower-case line of business."
            );
        }
        try {
            Report report = new Reconciler().reconcile(ediReferenceDir, ediCandidateDir, lineOfBusiness);

            List<EdiComparisonDto.ClientComparison> clients = new ArrayList<>();
            for (ClientResult result : report.results()) {
                List<EdiComparisonDto.Difference> differences = result.findings().stream()
                    .map(finding -> new EdiComparisonDto.Difference(finding.type().name(), finding.memberId(),
                        finding.detail(), finding.likelyCause()))
                    .toList();
                clients.add(new EdiComparisonDto.ClientComparison(result.client().id(), result.referenceCount(),
                    result.candidateCount(), differences));
            }
            List<String> skipped = report.skipped().stream()
                .map(client -> client.clientId() + ": " + client.reason())
                .toList();
            return new EdiComparisonDto(true, report.referenceName(), report.candidateName(), lineOfBusiness,
                clients.size(), (int) report.findingCount(), clients, skipped, null);
        } catch (IOException | RuntimeException e) {
            return EdiComparisonDto.error(
                "ERR_EDI_DATA_UNAVAILABLE",
                "The EDI data for the configured environments could not be read.",
                "Do not retry. Report that the EDI environments are not available."
            );
        }
    }

    @PreAuthorize("hasAuthority('SCOPE_logs:read')")
    @McpTool(
        name = "analyze_error_logs",
        title = "Analyze error logs",
        annotations = @McpTool.McpAnnotations(
            title = "Analyze error logs", readOnlyHint = true, destructiveHint = false,
            idempotentHint = true, openWorldHint = false),
        description = "Reads the service's error logs, groups repeated errors, and for each one returns the class "
            + "and line, the failing code, a root cause, a suggested fix, a regression test draft, and whether "
            + "the error still appears in the post-fix logs. Personal data in log text is masked. Read-only."
    )
    public ErrorAnalysisDto analyzeErrorLogs() {
        try {
            LogReader reader = new LogReader();
            ErrorGrouper grouper = new ErrorGrouper(appPackage);
            SourceLocator locator = new SourceLocator(sourceRoot);
            Verifier verifier = new Verifier(grouper);
            RuleBasedAnalyst analyst = new RuleBasedAnalyst();
            List<LogEntry> postFixLogs = Files.exists(postFixLogsFile) ? reader.read(postFixLogsFile) : null;

            List<ErrorAnalysisDto.ErrorFinding> findings = new ArrayList<>();
            for (ErrorGroup group : grouper.group(reader.read(logsFile))) {
                StackFrame frame = group.appFrame();
                SourceSnippet snippet = locator.locate(frame);
                Diagnosis diagnosis = analyst.diagnose(group, snippet);
                Verification verification = verifier.verify(group, postFixLogs);
                String status = !verification.checked() ? "NOT_CHECKED"
                    : verification.resolved() ? "RESOLVED"
                    : "STILL_OCCURRING (" + verification.occurrencesAfter() + " in post-fix logs)";

                findings.add(new ErrorAnalysisDto.ErrorFinding(
                    group.trace().exceptionType(),
                    // Log text can carry customer data; mask it before it reaches the model.
                    PiiMasker.mask(group.trace().exceptionMessage()),
                    group.count(),
                    frame == null ? null : frame.className(),
                    frame == null ? null : frame.method(),
                    frame == null ? null : frame.line(),
                    snippet == null ? null : snippet.failingLine().trim(),
                    PiiMasker.mask(diagnosis.rootCause()),
                    diagnosis.suggestedFix(),
                    diagnosis.regressionTest(),
                    status));
            }
            return new ErrorAnalysisDto(true, findings.size(), findings, null);
        } catch (IOException | RuntimeException e) {
            return ErrorAnalysisDto.error(
                "ERR_LOGS_UNAVAILABLE",
                "The configured log files could not be read.",
                "Do not retry. Report that the logs are not available."
            );
        }
    }

    @PreAuthorize("hasAuthority('SCOPE_source:read')")
    @McpTool(
        name = "get_java_source",
        title = "Get Java source",
        annotations = @McpTool.McpAnnotations(
            title = "Get Java source", readOnlyHint = true, destructiveHint = false,
            idempotentHint = true, openWorldHint = false),
        description = "Returns the lines around a given line of an application class, for example the top "
            + "application frame of a stack trace. Only classes in the application package can be read. Read-only."
    )
    public SourceSnippetDto getJavaSource(
            @McpToolParam(description = "Fully qualified class name, e.g. demo.orders.OrderService", required = true)
            String className,
            @McpToolParam(description = "1-based line number to centre the snippet on", required = true)
            Integer line) {

        // Guardrail: plain class names inside the application package only, so the tool cannot read other files.
        if (className == null || !CLASS_NAME.matcher(className).matches()
                || !className.startsWith(appPackage + ".")) {
            return SourceSnippetDto.error(
                "ERR_CLASS_NOT_ALLOWED",
                "className must be a fully qualified class in the application package '" + appPackage + "'.",
                "Use a class name taken from an application frame of the stack trace."
            );
        }
        if (line == null || line < 1) {
            return SourceSnippetDto.error(
                "ERR_INVALID_LINE",
                "line must be 1 or greater.",
                "Use the line number from the stack trace frame."
            );
        }
        try {
            SourceSnippet snippet = new SourceLocator(sourceRoot)
                .locate(new StackFrame(className, "", className.substring(className.lastIndexOf('.') + 1) + ".java", line));
            if (snippet == null) {
                return SourceSnippetDto.error(
                    "ERR_SOURCE_NOT_FOUND",
                    "No source was found for that class and line.",
                    "Check the class name and line number against the stack trace."
                );
            }
            return new SourceSnippetDto(true, className, snippet.line(), snippet.startLine(), snippet.lines(), null);
        } catch (IOException | RuntimeException e) {
            return SourceSnippetDto.error(
                "ERR_SOURCE_UNAVAILABLE",
                "The source could not be read.",
                "Do not retry. Report that the source is not available."
            );
        }
    }
}
