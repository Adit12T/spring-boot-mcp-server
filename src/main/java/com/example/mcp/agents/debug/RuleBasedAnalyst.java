package com.example.mcp.agents.debug;

import com.example.mcp.agents.debug.Model.Diagnosis;
import com.example.mcp.agents.debug.Model.ErrorGroup;
import com.example.mcp.agents.debug.Model.SourceSnippet;
import com.example.mcp.agents.debug.Model.StackFrame;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rule-based analyst for common exceptions. It drafts a regression test skeleton, not a finished test. */
public class RuleBasedAnalyst implements Analyst {

    private static final Pattern NULL_VARIABLE = Pattern.compile("because \"(.+?)\" is null");

    @Override
    public Diagnosis diagnose(ErrorGroup group, SourceSnippet snippet) {
        String failingLine = snippet == null ? "" : snippet.failingLine().trim();
        String message = group.trace().exceptionMessage();
        String rootCause;
        String fix;
        String scenario;

        switch (group.trace().simpleType()) {
            case "NullPointerException" -> {
                Matcher m = NULL_VARIABLE.matcher(message);
                String variable = m.find() ? m.group(1) : "a value";
                rootCause = "`" + variable + "` is null when this line runs: `" + failingLine + "`. "
                        + "The code assumes the value is always present.";
                fix = "Handle the missing value before using it: return early, apply a default, "
                        + "or reject the input with a clear error.";
                scenario = "when" + capitalize(identifier(variable)) + "IsNull";
            }
            case "ArithmeticException" -> {
                boolean sizeDivisor = failingLine.contains(".size()") || failingLine.contains(".length");
                rootCause = "Integer division by zero at: `" + failingLine + "`."
                        + (sizeDivisor ? " The divisor is a collection size, so the input was empty." : "");
                fix = "Guard the divisor: return a defined result for the empty case before dividing.";
                scenario = sizeDivisor ? "whenInputIsEmpty" : "whenDivisorIsZero";
            }
            case "ArrayIndexOutOfBoundsException", "IndexOutOfBoundsException", "StringIndexOutOfBoundsException" -> {
                rootCause = "An index is outside the valid range at: `" + failingLine + "` (" + message + ").";
                fix = "Check the index against the size before the access, or iterate without manual indexes.";
                scenario = "whenIndexIsOutOfRange";
            }
            case "NumberFormatException" -> {
                rootCause = "Text that is not a number was parsed at: `" + failingLine + "` (" + message + ").";
                fix = "Validate or trim the input before parsing and return a clear validation error.";
                scenario = "whenInputIsNotNumeric";
            }
            default -> {
                rootCause = group.trace().simpleType() + " at: `" + failingLine + "` (" + message + "). "
                        + "No rule covers this exception type; review the snippet.";
                fix = "Review the failing line and its inputs.";
                scenario = "reproducesLoggedFailure";
            }
        }
        return new Diagnosis(rootCause, fix, testSkeleton(group, scenario), "rule-based (offline)");
    }

    private String testSkeleton(ErrorGroup group, String scenario) {
        StackFrame frame = group.appFrame();
        if (frame == null) {
            return null;
        }
        String className = frame.simpleClassName();
        return """
                package %s;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.fail;

                // Drafted by the debugging agent for %s in %s.%s (line %d).
                class %s%sRegressionTest {

                    @Test
                    void %s_%s() {
                        // Arrange: build the input that reproduces the logged failure.
                        // Act + Assert: after the fix, calling %s must not throw %s.
                        fail("Complete the arrange step, then assert the fixed behaviour.");
                    }
                }
                """.formatted(frame.packageName(), group.trace().simpleType(), className, frame.method(),
                frame.line(), className, capitalize(frame.method()), frame.method(), scenario, frame.method(), group.trace().simpleType());
    }

    private static String identifier(String text) {
        String cleaned = text.replaceAll("[^A-Za-z0-9]", "");
        return cleaned.isEmpty() ? "value" : cleaned;
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
