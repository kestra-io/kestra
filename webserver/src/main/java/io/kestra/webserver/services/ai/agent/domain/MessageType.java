package io.kestra.webserver.services.ai.agent.domain;

public enum MessageType {
    TEXT,
    TOOL_CALL,
    TOOL_RESULT,
    PROPOSED_ACTION
}
