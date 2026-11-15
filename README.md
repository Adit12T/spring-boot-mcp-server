# Secure Spring Boot MCP Server

A reference **Model Context Protocol (MCP) Server** showing production security and resilience patterns, built with **Spring Boot 3.4**, **Spring AI**, **Spring Security OAuth 2.1**, **Redis Idempotency**, and **Resilience4j Bulkheads**.

> **Scope.** This is a demo. It contains no employer code or data. The order and refund domain is an in-memory stub, and the EDI files, logs and `sample-app` are synthetic.

## Architecture Overview

1. **Layer 1: Edge & Transport Boundary** — Streamable HTTP (`/mcp`) protected by `McpServerOAuth2Configurer` with RFC 9728 Protected Resource Metadata (`/.well-known/oauth-protected-resource`).
2. **Layer 2: Session & Protocol Engine** — Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`) with `McpSyncServer`.
3. **Layer 3: Capability & Guardrail Layer** — Declarative `@McpTool` and `@McpResource` components with `@PreAuthorize` scope verification, semantic validation, and `@Bulkhead` concurrency limits.
4. **Layer 4: Domain & Infrastructure** — Clean domain services backed by Redis atomic idempotency locks (`SET NX EX`).

## Tools

| Tool | Required scope | What it does |
|---|---|---|
| `get_order_status` | `orders:read` | Returns the status of an order for the authenticated tenant |
| `issue_customer_refund` | `payments:refund` | Issues a refund up to $500, with an idempotency key and a concurrency limit |
| `compare_edi_environments` | `edi:read` | Compares EDI files between two environments for a line of business and returns missing, extra, duplicate and changed members with likely causes |
| `analyze_error_logs` | `logs:read` | Groups errors from the logs and returns class and line, root cause, suggested fix, a regression test draft, and whether the error still occurs after the fix |
| `get_java_source` | `source:read` | Returns the lines around a line of an application class |

The three engineering tools are read-only and run on the sample data in `sample-data/` and `sample-app/`.
Their logic is the same code as my two agent demos, packaged under `com.example.mcp.agents`:
the EDI reconciliation agent and the Java debugging agent.

### Guardrails on the engineering tools

- **One scope per tool.** A token that can read logs cannot issue refunds.
- **Inputs that reach the file system are validated.** `lineOfBusiness` must be a plain lower-case name, and
  `get_java_source` only accepts class names inside the configured application package, so neither tool can be
  steered to other files.
- **Errors are structured.** Each tool returns an error code, a message and a recovery hint instead of a stack trace,
  so the agent knows whether to retry.

## Quick Start

### 1. Start Local Infrastructure (Redis & Keycloak)
```bash
docker compose up -d
```

### 2. Run the MCP Server
```bash
mvn spring-boot:run
```

### 3. Test with MCP Inspector
```bash
npx @modelcontextprotocol/inspector
```
- **Transport Type**: `Streamable HTTP`
- **URL**: `http://localhost:8080/mcp`
- **Authorization**: Supply a valid Bearer JWT carrying the scope for the tool you call, for example `orders:read` or `edi:read`.

## Tests

```bash
mvn test
```

`EngineeringMcpToolsTests` runs the three engineering tools against the sample data, including the input guardrails.

## Configuration

The engineering tools read their data locations from `application.yml` (`demo.edi.*`, `demo.logs.*`, `demo.source.*`).
Each can be overridden with an environment variable, for example `EDI_CANDIDATE_DIR`.
