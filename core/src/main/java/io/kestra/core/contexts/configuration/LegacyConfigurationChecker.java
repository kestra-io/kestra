package io.kestra.core.contexts.configuration;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.exceptions.KestraRuntimeException;

import io.micronaut.context.env.Environment;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects, at startup, configuration properties left over from a previous major version.
 * <p>
 * Such properties are silently ignored by Micronaut, so an instance upgraded without migrating its configuration
 * starts with defaults the operator never chose. Properties whose loss only changes a tunable are reported as a
 * warning, the ones that change the behaviour of the instance fail the startup.
 */
@Singleton
@Slf4j
public class LegacyConfigurationChecker {

    private static final String MIGRATION_GUIDE_URL = "https://kestra.io/docs/migration-guide/v2.0.0";

    private final Environment environment;
    private final List<LegacyConfigurationProvider> providers;

    @Inject
    public LegacyConfigurationChecker(final Environment environment, final List<LegacyConfigurationProvider> providers) {
        this.environment = Objects.requireNonNull(environment);
        this.providers = Objects.requireNonNull(providers);
    }

    /**
     * Reports every legacy property still configured on this instance.
     *
     * @throws KestraRuntimeException if at least one of them is declared with {@link LegacyConfiguration.Severity#ERROR}.
     */
    public void check() {
        List<LegacyConfiguration> configured = providers.stream()
            .map(LegacyConfigurationProvider::legacyConfigurations)
            .flatMap(List::stream)
            .filter(this::isConfigured)
            .sorted(Comparator.comparing(LegacyConfiguration::key))
            .toList();

        if (configured.isEmpty()) {
            return;
        }

        // one log line per property, so that each one stands out in the startup logs
        filterBySeverity(configured, LegacyConfiguration.Severity.WARN)
            .forEach(legacy -> log.warn("{} This property is no longer supported and is ignored, read the migration guide at {}", legacy.describe(), MIGRATION_GUIDE_URL));

        List<LegacyConfiguration> errors = filterBySeverity(configured, LegacyConfiguration.Severity.ERROR);
        if (!errors.isEmpty()) {
            errors.forEach(
                legacy -> log
                    .error("{} This property is no longer supported and changed the behaviour of this instance, read the migration guide at {}", legacy.describe(), MIGRATION_GUIDE_URL)
            );
            throw new KestraRuntimeException(
                "Your configuration contains properties that are no longer supported and that changed the behaviour of this instance: %s. Read the migration guide at %s, then update your configuration."
                    .formatted(
                        errors.stream().map(legacy -> "`" + legacy.key() + "`").collect(Collectors.joining(", ")),
                        MIGRATION_GUIDE_URL
                    )
            );
        }
    }

    private boolean isConfigured(final LegacyConfiguration legacy) {
        return keyVariants(legacy.key())
            .anyMatch(key -> environment.containsProperty(key) || environment.containsProperties(key));
    }

    /**
     * A property set through an environment variable may reach Micronaut with its hyphens turned into dots, as
     * {@code KESTRA_WEBSERVER_GOOGLE_ANALYTICS} carries no way to tell both separators apart, so both spellings
     * are looked up.
     */
    private static Stream<String> keyVariants(final String key) {
        String dotted = key.replace('-', '.');
        return dotted.equals(key) ? Stream.of(key) : Stream.of(key, dotted);
    }

    private static List<LegacyConfiguration> filterBySeverity(final List<LegacyConfiguration> configured, final LegacyConfiguration.Severity severity) {
        return configured.stream().filter(legacy -> severity == legacy.severity()).toList();
    }
}
