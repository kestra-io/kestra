package io.kestra.webserver.services.ai.agent.data;

import java.util.Map;

import io.kestra.core.ai.agent.models.AgentMode;

import io.micronaut.core.annotation.Nullable;

/**
 * A single chat turn request.
 *
 * <p>
 * {@code additionalContext} is arbitrary, caller-supplied context (e.g. what the user is currently
 * looking at) that is rendered into the conversation for this turn only: it is appended at the end of
 * the model input and is <em>not</em> persisted in the thread's message history.
 * </p>
 */
public record ApiChatTurnRequest(
    String prompt,
    @Nullable AgentMode mode,
    @Nullable Map<String, Object> additionalContext,
    @Nullable String providerId) {
}
