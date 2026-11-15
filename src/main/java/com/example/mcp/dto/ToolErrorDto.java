package com.example.mcp.dto;

/** A structured error the model can act on, instead of a stack trace. */
public record ToolErrorDto(String errorCode, String errorMessage, String recoveryHint) {}
