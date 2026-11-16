package com.example.mcp;

import com.example.mcp.dto.EdiComparisonDto;
import com.example.mcp.mcp.EngineeringMcpTools;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the full application context (no Redis or identity provider needed) and checks the security
 * boundary: the MCP endpoint rejects anonymous calls, advertises its OAuth metadata, and every tool
 * enforces its own scope.
 */
@SpringBootTest
@AutoConfigureMockMvc
class McpServerSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EngineeringMcpTools engineeringTools;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void mcpEndpointRejectsAnonymousCallsAndPointsToItsResourceMetadata() throws Exception {
        mockMvc.perform(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", containsString("resource_metadata=")));
    }

    @Test
    void protectedResourceMetadataIsPublic() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resource").value(containsString("/mcp")))
            .andExpect(jsonPath("$.authorization_servers").isArray());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_edi:read")
    void toolRunsWhenTheCallerHasItsScope() {
        EdiComparisonDto result = engineeringTools.compareEdiEnvironments("medical");

        assertTrue(result.success());
        assertEquals(6, result.differencesFound());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_orders:read")
    void toolIsDeniedWithoutItsScopeAndTheDenialIsRecorded() {
        assertThrows(AccessDeniedException.class, () -> engineeringTools.compareEdiEnvironments("medical"));

        assertTrue(meterRegistry.get("mcp.tool.calls")
            .tag("tool", "compare_edi_environments")
            .tag("outcome", "DENIED")
            .timer()
            .count() >= 1);
    }
}
