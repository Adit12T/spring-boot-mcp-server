package com.example.mcp.agents.debug;

import com.example.mcp.agents.debug.Model.Diagnosis;
import com.example.mcp.agents.debug.Model.ErrorGroup;
import com.example.mcp.agents.debug.Model.SourceSnippet;

/** Explains an error group: root cause, suggested fix, and a regression test draft. */
public interface Analyst {

    Diagnosis diagnose(ErrorGroup group, SourceSnippet snippet);
}
