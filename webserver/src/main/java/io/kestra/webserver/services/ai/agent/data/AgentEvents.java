package io.kestra.webserver.services.ai.agent.data;

import java.util.Map;

import io.micronaut.core.annotation.Nullable;

public final class AgentEvents {
    public static final String TOKEN = "token";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String PROPOSED_ACTION = "proposed_action";
    public static final String DONE = "done";

    private AgentEvents() {
    }

    public record TokenEvent(String text) {
    }

    public record ToolCallEvent(String tool, @Nullable String family, Map<String, Object> arguments) {
    }

    public record ToolResultEvent(String tool, String outcome) {
    }

    public record ProposedActionEvent(
        String confirmationId,
        @Nullable String tool,
        @Nullable String family,
        String summary,
        @Nullable Map<String, Object> arguments
    ) {
    }

    public record DoneEvent(String status) {
    }
}
