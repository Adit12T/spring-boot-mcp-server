package com.example.mcp.dto;

/** Implemented by tool results that can report a handled failure, so audit and metrics can tell it from a success. */
public interface ToolResult {

    boolean success();
}
