package io.kestra.webserver.services.ai.agent.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.webserver.services.ai.AiProviderConfiguration;
import io.kestra.webserver.services.ai.AiProvidersConfiguration;
import io.kestra.webserver.services.ai.agent.AgentConfiguration;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The largest tool result, in characters, a turn may send to the model. The instance default comes from
 * {@code kestra.ai.agent.max-tool-result-chars}; a provider overrides it with a {@code maxToolResultChars}
 * entry in its own {@code configuration}.
 */
@Singleton
public class ToolResultBudget {
    static final String PROVIDER_OVERRIDE_KEY = "maxToolResultChars";

    private final int defaultChars;
    private final Map<String, Integer> overrides;

    @Inject
    public ToolResultBudget(final AgentConfiguration agentConfiguration, final AiProvidersConfiguration providers) {
        this(agentConfiguration.maxToolResultChars(), providers.providers());
    }

    public ToolResultBudget(final int defaultChars, @Nullable final List<AiProviderConfiguration> providers) {
        if (defaultChars <= 0) {
            throw new IllegalArgumentException("kestra.ai.agent.max-tool-result-chars must be greater than 0, got %d.".formatted(defaultChars));
        }
        this.defaultChars = defaultChars;
        this.overrides = new HashMap<>();
        if (providers != null) {
            for (AiProviderConfiguration provider : providers) {
                Integer override = overrideOf(provider);
                if (override != null) {
                    overrides.put(provider.id(), override);
                }
            }
        }
    }

    /**
     * @param providerId the provider the turn runs on, or {@code null} for the default provider
     * @return the maximum size, in characters, of one tool result for that provider
     */
    public int limitFor(@Nullable final String providerId) {
        return providerId == null ? defaultChars : overrides.getOrDefault(providerId, defaultChars);
    }

    @Nullable
    private static Integer overrideOf(final AiProviderConfiguration provider) {
        if (provider.configuration() == null || !provider.configuration().containsKey(PROVIDER_OVERRIDE_KEY)) {
            return null;
        }
        Object raw = provider.configuration().get(PROVIDER_OVERRIDE_KEY);
        int value;
        try {
            value = raw instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Provider '%s' has an invalid %s: '%s'. It must be a whole number of characters.".formatted(provider.id(), PROVIDER_OVERRIDE_KEY, raw), e);
        }
        if (value <= 0) {
            throw new IllegalArgumentException("Provider '%s' has %s %d. It must be greater than 0.".formatted(provider.id(), PROVIDER_OVERRIDE_KEY, value));
        }
        return value;
    }
}
