package io.kestra.webserver.services.ai.agent;

import io.kestra.core.ai.agent.models.AgentMode;

import io.micronaut.core.annotation.Nullable;

/**
 * Resolves the system prompt used for a Copilot turn.
 * <p>
 * The OSS default ({@link DefaultSystemPromptResolver}) always returns the built-in per-mode prompt.
 * This is the neutral extension point EE replaces to support per-provider custom system prompts.
 */
public interface SystemPromptResolver {

    /**
     * Returns the system prompt to use for a turn.
     *
     * @param mode          the conversation mode.
     * @param providerId    the AI provider id selected for the turn, or {@code null} when the default provider is used.
     * @param defaultPrompt the built-in prompt for {@code mode}; the fallback when no custom prompt applies.
     * @return the prompt to send to the model — never {@code null}.
     */
    String resolve(AgentMode mode, @Nullable String providerId, String defaultPrompt);
}
