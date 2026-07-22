package io.kestra.webserver.services.ai.agent.data;

import java.util.Map;

import io.micronaut.core.annotation.Nullable;

public final class AgentEvents {
    public static final String TOKEN = "token";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String PROPOSED_ACTION = "proposed_action";
    public static final String ARTEFACT_DRAFT = "artefact_draft";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    private AgentEvents() {
    }

    public record TokenEvent(String text) {
    }

    public record ToolCallEvent(String tool, String kind, @Nullable String family, Map<String, Object> arguments) {
    }

    public record ToolResultEvent(String tool, String outcome) {
    }

    public record ProposedActionEvent(
        String confirmationId,
        @Nullable String tool,
        @Nullable String family,
        String summary,
        @Nullable Map<String, Object> arguments) {
    }

    public record ArtefactDraftEvent(
        String draftId,
        String kind,
        String yaml,
        boolean valid,
        @Nullable String constraints) {
    }

    public record DoneEvent(String status) {
    }

    /**
     * A terminal failure surfaced as a normal SSE event (not a reactive/transport error), so the
     * client receives the failure reason even once the {@code text/event-stream} response has been
     * committed. The stream is completed right after this event; no {@link DoneEvent} follows.
     */
    public record ErrorEvent(String message) {
    }
}
