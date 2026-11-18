package com.example.mcp;

import com.example.mcp.dto.EdiComparisonDto;
import com.example.mcp.mcp.EngineeringMcpTools;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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
 * Starts the full application context against a stand-in identity provider (no Redis or Keycloak needed)
 * and checks the security
 * boundary: the MCP endpoint rejects anonymous calls, advertises its OAuth metadata, and every tool
 * enforces its own scope.
 */
@SpringBootTest
@AutoConfigureMockMvc
class McpServerSecurityIntegrationTests {

    private static final RSAKey SIGNING_KEY = generateSigningKey();

    /** Serves the OpenID discovery document and signing key the resource server reads at startup. */
    private static final HttpServer IDENTITY_PROVIDER = startIdentityProvider();

    private static RSAKey generateSigningKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").algorithm(JWSAlgorithm.RS256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String issuer() {
        return "http://localhost:" + IDENTITY_PROVIDER.getAddress().getPort();
    }

    /** A signed access token, as the identity provider would issue it. */
    private static String token(String audience, String scope) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject("agent-1")
            .audience(audience)
            .claim("scope", scope)
            .claim("tenant_id", "tenant-1")
            .issueTime(new Date())
            .expirationTime(new Date(System.currentTimeMillis() + 300_000))
            .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(SIGNING_KEY));
        return jwt.serialize();
    }

    private static HttpServer startIdentityProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            String issuer = "http://localhost:" + server.getAddress().getPort();
            byte[] discovery = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}")
                .getBytes(StandardCharsets.UTF_8);
            byte[] keys = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/.well-known/openid-configuration", exchange -> respond(exchange, discovery));
            server.createContext("/jwks", exchange -> respond(exchange, keys));
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @DynamicPropertySource
    static void identityProvider(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            McpServerSecurityIntegrationTests::issuer);
    }

    @AfterAll
    static void stopIdentityProvider() {
        IDENTITY_PROVIDER.stop(0);
    }

    private static final String INITIALIZE = """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26",
         "capabilities":{},"clientInfo":{"name":"integration-test","version":"1.0.0"}}}
        """;

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
    void rejectsAValidTokenThatWasIssuedForAnotherResource() throws Exception {
        mockMvc.perform(post("/mcp")
                .header("Authorization", "Bearer " + token("http://localhost/some-other-api", "edi:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(INITIALIZE))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsATokenIssuedForThisServer() throws Exception {
        mockMvc.perform(post("/mcp")
                .header("Authorization", "Bearer " + token("http://localhost/mcp", "edi:read"))
                .header("Accept", "application/json, text/event-stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(INITIALIZE))
            .andExpect(status().isOk());
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
