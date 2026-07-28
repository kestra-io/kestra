package io.kestra.webserver.services.ai.agent;

import io.kestra.core.ai.agent.models.AgentMode;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * OSS default: always uses the built-in per-mode prompt. Custom, per-provider prompts are an EE
 * feature — EE replaces this bean via {@code @Replaces(SystemPromptResolver.class)}.
 */
@Singleton
public class DefaultSystemPromptResolver implements SystemPromptResolver {

    @Override
    public String resolve(final AgentMode mode, @Nullable final String providerId, final String defaultPrompt) {
        return defaultPrompt;
    }
}
