package io.kestra.core.contexts.configuration;

import java.util.List;

/**
 * Contributes the legacy configuration properties of one edition or module.
 * <p>
 * Micronaut has no registry of the valid property keys — plugins contribute arbitrary ones — so unknown properties
 * cannot be detected generically. Instead, every property that is dropped or renamed is declared here, and
 * {@link LegacyConfigurationChecker} reports the ones still present at startup.
 * <p>
 * A key declared here must first be verified as no longer bound anywhere, which no test can prove: searching for
 * its literal spelling is not enough, as Micronaut binds {@code kestra.webserver.google-analytics} to a
 * {@code googleAnalytics} record component that a search for the hyphenated key never matches. Warning about a
 * property that still works is worse than not warning at all, because an operator following the advice loses a
 * working feature.
 */
public interface LegacyConfigurationProvider {

    List<LegacyConfiguration> legacyConfigurations();
}
