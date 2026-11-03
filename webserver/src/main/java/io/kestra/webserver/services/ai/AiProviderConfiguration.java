package io.kestra.webserver.services.ai;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.ai.agent.models.AgentMode;

import io.micronaut.core.annotation.Nullable;

public record AiProviderConfiguration(
    String id,
    String displayName,
    String type,
    boolean isDefault,
    @Nullable Map<String, Object> configuration,
    @Nullable SystemPrompt systemPrompt,
    @JsonProperty("default") boolean defaultFlag) {

    /** Back-compat constructor for call sites that predate the optional custom system prompt. */
    public AiProviderConfiguration(String id, String displayName, String type, boolean isDefault, @Nullable Map<String, Object> configuration) {
        this(id, displayName, type, isDefault, configuration, null);
    }

    /** Back-compat constructor for call sites that predate the {@code default} key. */
    public AiProviderConfiguration(String id, String displayName, String type, boolean isDefault, @Nullable Map<String, Object> configuration, @Nullable SystemPrompt systemPrompt) {
        this(id, displayName, type, isDefault, configuration, systemPrompt, false);
    }

    /**
     * Whether this provider is the default one, set by either the {@code default} key or the older
     * {@code is-default} key, which is kept for backward compatibility.
     */
    public boolean isDefaultProvider() {
        return isDefault || defaultFlag;
    }

    /**
     * Optional per-mode custom system prompts for this provider. A non-blank value for a mode fully
     * replaces the built-in prompt for that mode; a blank/absent value keeps the built-in one.
     * <p>
     * This is an EE-only feature: only the EE {@code SystemPromptResolver} acts on it; in OSS the field
     * is inert (the built-in per-mode prompt is always used).
     */
    public record SystemPrompt(
        @Nullable String ask,
        @Nullable String plan,
        @Nullable String edit) {

        /** The configured prompt for {@code mode}, or {@code null} when none is set for it. */
        @Nullable
        public String forMode(final AgentMode mode) {
            return switch (mode) {
                case ASK -> ask;
                case PLAN -> plan;
                case EDIT -> edit;
            };
        }
    }
}
