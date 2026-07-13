package io.kestra.webserver.services.ai.agent.domain;

public enum AgentMessageType {
    TEXT,
    TOOL_CALL,
    TOOL_RESULT,
    PROPOSED_ACTION,
    ARTEFACT_DRAFT,
    CANCELLED
}
