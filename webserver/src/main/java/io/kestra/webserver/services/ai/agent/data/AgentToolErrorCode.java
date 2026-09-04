package io.kestra.webserver.services.ai.agent.data;

/**
 * A machine-readable discriminator for a failed tool call, carried on the tool result — both streamed to
 * the client and persisted alongside the result — so the UI can render its own message for a known
 * failure instead of echoing the model-facing text. A failure with no code is an ordinary tool error and
 * carries only its message.
 */
public enum AgentToolErrorCode {
    /** The tool returned more than {@code kestra.ai.agent.max-tool-result-chars} and was failed rather than truncated. */
    RESULT_TOO_LARGE
}
