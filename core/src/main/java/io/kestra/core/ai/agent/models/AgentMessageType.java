package io.kestra.core.ai.agent.models;

public enum AgentMessageType {
    TEXT,
    TOOL_CALL,
    TOOL_RESULT,
    PROPOSED_ACTION,
    ARTEFACT_DRAFT,
    CANCELLED
}
