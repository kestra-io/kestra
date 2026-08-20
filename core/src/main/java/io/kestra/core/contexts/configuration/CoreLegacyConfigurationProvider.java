package io.kestra.core.contexts.configuration;

import java.util.List;

import io.kestra.core.contexts.configuration.LegacyConfiguration.Severity;

import jakarta.inject.Singleton;

/**
 * The Open Source Edition properties that were valid in 1.x and are ignored since 2.0.
 */
@Singleton
public class CoreLegacyConfigurationProvider implements LegacyConfigurationProvider {

    private static final List<LegacyConfiguration> LEGACY_CONFIGURATIONS = List.of(
        // the JDBC cleaner has been removed together with the legacy JDBC queue it was purging
        LegacyConfiguration.removed("kestra.jdbc.cleaner", Severity.WARN),
        LegacyConfiguration.removed("kestra.jdbc.executor.thread-count", Severity.WARN),
        LegacyConfiguration.removed("kestra.jdbc.executor.clean", Severity.WARN),
        LegacyConfiguration.removed("kestra.templates.enabled", Severity.WARN),
        LegacyConfiguration.removed("kestra.webserver.google-analytics", Severity.WARN),
        LegacyConfiguration.removed("kestra.webserver.html-title", Severity.WARN),
        LegacyConfiguration.removed("kestra.webserver.html-head", Severity.WARN),
        LegacyConfiguration.removed("kestra.plugins.local-repository-path", Severity.WARN)
    );

    @Override
    public List<LegacyConfiguration> legacyConfigurations() {
        return LEGACY_CONFIGURATIONS;
    }
}
