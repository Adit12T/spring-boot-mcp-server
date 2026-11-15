package com.example.mcp;

import com.example.mcp.dto.EdiComparisonDto;
import com.example.mcp.dto.ErrorAnalysisDto;
import com.example.mcp.dto.SourceSnippetDto;
import com.example.mcp.mcp.EngineeringMcpTools;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the engineering tools directly against the sample data, without starting Spring. */
class EngineeringMcpToolsTests {

    private final EngineeringMcpTools tools = new EngineeringMcpTools(
        "sample-data/edi/env-a",
        "sample-data/edi/env-b",
        "sample-data/logs/logs-before.ndjson",
        "sample-data/logs/logs-after.ndjson",
        "sample-app/src/main/java",
        "demo.orders");

    @Test
    void comparesEdiEnvironments() {
        EdiComparisonDto result = tools.compareEdiEnvironments("medical");

        assertTrue(result.success());
        assertEquals(3, result.clientsCompared());
        assertEquals(6, result.differencesFound());
        assertEquals(List.of("DUPLICATE", "CHANGED", "MISSING", "CHANGED", "EXTRA"),
            result.clients().get(0).differences().stream().map(EdiComparisonDto.Difference::type).toList());
        assertEquals(2, result.skippedClients().size());
    }

    @Test
    void rejectsALineOfBusinessThatIsNotAPlainName() {
        EdiComparisonDto result = tools.compareEdiEnvironments("../../etc");

        assertFalse(result.success());
        assertEquals("ERR_INVALID_LINE_OF_BUSINESS", result.error().errorCode());
    }

    @Test
    void analyzesErrorLogs() {
        ErrorAnalysisDto result = tools.analyzeErrorLogs();

        assertTrue(result.success());
        assertEquals(2, result.distinctErrors());

        ErrorAnalysisDto.ErrorFinding nullPointer = result.findings().get(0);
        assertEquals("java.lang.NullPointerException", nullPointer.exceptionType());
        assertEquals(3, nullPointer.occurrences());
        assertEquals("demo.orders.DiscountService", nullPointer.className());
        assertTrue(nullPointer.failingCode().contains("coupon.percentOff()"));
        assertEquals("RESOLVED", nullPointer.verification());
        assertTrue(result.findings().get(1).verification().startsWith("STILL_OCCURRING"));
    }

    @Test
    void returnsSourceAroundALine() {
        SourceSnippetDto result = tools.getJavaSource("demo.orders.OrderService", 25);

        assertTrue(result.success());
        assertTrue(result.lines().get(25 - result.startLine()).contains("itemPricesCents.size()"));
    }

    @Test
    void refusesSourceOutsideTheApplicationPackage() {
        assertEquals("ERR_CLASS_NOT_ALLOWED",
            tools.getJavaSource("java.lang.String", 1).error().errorCode());
        assertEquals("ERR_CLASS_NOT_ALLOWED",
            tools.getJavaSource("demo.orders/../../../etc/passwd", 1).error().errorCode());
        assertEquals("ERR_SOURCE_NOT_FOUND",
            tools.getJavaSource("demo.orders.DoesNotExist", 1).error().errorCode());
    }
}
