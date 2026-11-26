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

    /**
     * Mirrors the keys persisted with a tool result so the live stream and a thread reload expose the
     * same detail to the client.
     *
     * @param error  the failure detail when {@code outcome} is {@code "error"} (the tool threw); else {@code null}.
     * @param reason the rejection detail when {@code outcome} is {@code "rejected"}; else {@code null}.
     */
    public record ToolResultEvent(String tool, String outcome, @Nullable String error, @Nullable String reason) {
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

    /**
     * @param status the resting thread status the stream closed into.
     * @param reason why the turn ended without any output; {@code null} whenever the turn produced something.
     */
    public record DoneEvent(String status, @Nullable EmptyTurnReason reason) {
        public DoneEvent(final String status) {
            this(status, null);
        }
    }

    /**
     * Why the model call succeeded yet returned neither text nor a tool call.
     */
    public enum EmptyTurnReason {
        /** The provider stopped normally with nothing to say; usually transient, so worth retrying. */
        EMPTY_RESPONSE,
        /** The output-token budget ran out before any visible output was produced. */
        OUTPUT_LIMIT,
        /** The provider withheld the response (safety, recitation, blocklist, ...). */
        CONTENT_FILTERED
    }

    /**
     * A terminal failure surfaced as a normal SSE event (not a reactive/transport error), so the
     * client receives the failure reason even once the {@code text/event-stream} response has been
     * committed. The stream is completed right after this event; no {@link DoneEvent} follows.
     */
    public record ErrorEvent(String message) {
    }
}
