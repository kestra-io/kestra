package io.kestra.core.contexts;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.regex.Pattern;

/**
 * Kestra application properties.
 */
@Singleton
public class KestraConfig {
    public static final String DEFAULT_SYSTEM_FLOWS_NAMESPACE = "system";

    @Value("${kestra.system-flows.namespace:" + DEFAULT_SYSTEM_FLOWS_NAMESPACE + "}")
    private String systemFlowNamespace;

    public String getSystemFlowNamespace() {
        return systemFlowNamespace;
    }
}
