package io.kestra.core.contexts.configuration;

import java.util.List;

/**
 * Contributes the legacy configuration properties of one edition or module.
 * <p>
 * Micronaut has no registry of the valid property keys — plugins contribute arbitrary ones — so unknown properties
 * cannot be detected generically. Instead, every property that is dropped or renamed is declared here, and
 * {@link LegacyConfigurationChecker} reports the ones still present at startup.
 */
public interface LegacyConfigurationProvider {

    List<LegacyConfiguration> legacyConfigurations();
}
