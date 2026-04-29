package io.kestra.core.contexts;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Kestra application properties.
 */
@Singleton
public class KestraConfig {
    public static final String DEFAULT_SYSTEM_FLOWS_NAMESPACE = "system";

    @Inject
    private SystemFlowsConfiguration systemFlowsConfiguration;

    public String getSystemFlowNamespace() {
        return systemFlowsConfiguration.namespace();
    }
}
