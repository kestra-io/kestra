package io.kestra.core.contexts.configuration;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import io.kestra.core.contexts.configuration.LegacyConfiguration.Severity;
import io.kestra.core.exceptions.KestraRuntimeException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyConfigurationCheckerTest {

    @Test
    void shouldNotFailWhenNoLegacyConfigurationIsConfigured() {
        // Given
        LegacyConfigurationChecker checker = checker(
            Set.of(),
            LegacyConfiguration.removed("kestra.jdbc.cleaner", Severity.ERROR)
        );

        // When - Then
        assertThatCode(checker::check).doesNotThrowAnyException();
    }

    @Test
    void shouldNotFailWhenOnlyWarningLegacyConfigurationsAreConfigured() {
        // Given
        LegacyConfigurationChecker checker = checker(
            Set.of("kestra.templates.enabled"),
            LegacyConfiguration.removed("kestra.templates.enabled", Severity.WARN)
        );

        // When - Then
        assertThatCode(checker::check).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenAnErrorLegacyConfigurationIsConfigured() {
        // Given
        LegacyConfigurationChecker checker = checker(
            Set.of("micronaut.security.login.failed-attempts", "kestra.templates.enabled"),
            LegacyConfiguration.renamed("micronaut.security.login.failed-attempts", "kestra.security.login.failed-attempts", Severity.ERROR),
            LegacyConfiguration.removed("kestra.templates.enabled", Severity.WARN),
            LegacyConfiguration.removed("kestra.jdbc.cleaner", Severity.ERROR)
        );

        // When - Then
        assertThatThrownBy(checker::check)
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("`micronaut.security.login.failed-attempts`")
            .hasMessageContaining("https://kestra.io/docs/migration-guide/v2.0.0")
            // only the configured properties of the reported severity are listed
            .hasMessageNotContaining("kestra.jdbc.cleaner")
            .hasMessageNotContaining("kestra.templates.enabled");
    }

    @Test
    void shouldDetectLegacyConfigurationGivenItsEnvironmentVariableSpelling() {
        // Given, KESTRA_WEBSERVER_GOOGLE_ANALYTICS resolves as `kestra.webserver.google.analytics`
        LegacyConfigurationChecker checker = checker(
            Set.of("kestra.webserver.google.analytics"),
            LegacyConfiguration.removed("kestra.webserver.google-analytics", Severity.ERROR)
        );

        // When - Then
        assertThatThrownBy(checker::check)
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("`kestra.webserver.google-analytics`");
    }

    @Test
    void shouldDetectLegacyConfigurationGivenOnlyItsChildrenAreConfigured() {
        // Given
        Environment environment = mock(Environment.class);
        when(environment.containsProperty(anyString())).thenReturn(false);
        when(environment.containsProperties("kestra.jdbc.cleaner")).thenReturn(true);
        LegacyConfigurationChecker checker = new LegacyConfigurationChecker(
            environment,
            List.of(() -> List.of(LegacyConfiguration.removed("kestra.jdbc.cleaner", Severity.ERROR)))
        );

        // When - Then
        assertThatThrownBy(checker::check)
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("`kestra.jdbc.cleaner`");
    }

    @Test
    void shouldLogEachLegacyConfigurationOnItsOwnLine() {
        // Given
        LegacyConfigurationChecker checker = checker(
            Set.of("kestra.templates.enabled", "kestra.jdbc.executor.clean", "kestra.jdbc.cleaner", "kestra.indexer.models"),
            LegacyConfiguration.removed("kestra.templates.enabled", Severity.WARN),
            LegacyConfiguration.removed("kestra.jdbc.executor.clean", Severity.WARN),
            LegacyConfiguration.renamed("kestra.jdbc.cleaner", "kestra.jdbc.queue.cleaner", Severity.ERROR),
            LegacyConfiguration.removed("kestra.indexer.models", Severity.ERROR)
        );
        Logger logger = (Logger) LoggerFactory.getLogger(LegacyConfigurationChecker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        // When
        try {
            assertThatThrownBy(checker::check).isInstanceOf(KestraRuntimeException.class);
        } finally {
            logger.detachAppender(appender);
        }

        // Then
        assertThat(appender.list).extracting(ILoggingEvent::getLevel)
            .containsExactly(Level.WARN, Level.WARN, Level.ERROR, Level.ERROR);
        assertThat(appender.list).map(ILoggingEvent::getFormattedMessage)
            .allSatisfy(message -> assertThat(message).doesNotContain("\n").contains("https://kestra.io/docs/migration-guide/v2.0.0"))
            .anySatisfy(message -> assertThat(message).startsWith("`kestra.jdbc.executor.clean` has been removed."))
            .anySatisfy(message -> assertThat(message).startsWith("`kestra.templates.enabled` has been removed."))
            .anySatisfy(message -> assertThat(message).startsWith("`kestra.indexer.models` has been removed."))
            .anySatisfy(message -> assertThat(message).startsWith("`kestra.jdbc.cleaner` has been renamed to `kestra.jdbc.queue.cleaner`."));
    }

    @Test
    void shouldRejectABlankReplacement() {
        // When - Then
        assertThatThrownBy(() -> LegacyConfiguration.renamed("kestra.jdbc.cleaner", " ", Severity.WARN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectABlankKey() {
        // When - Then
        assertThatThrownBy(() -> LegacyConfiguration.removed("  ", Severity.WARN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDescribeARemovedAndARenamedConfiguration() {
        // When - Then
        assertThat(LegacyConfiguration.removed("kestra.templates.enabled", Severity.WARN).describe())
            .isEqualTo("`kestra.templates.enabled` has been removed.");
        assertThat(LegacyConfiguration.renamed("kestra.mail-service", "kestra.ee.mail-service", Severity.WARN).describe())
            .isEqualTo("`kestra.mail-service` has been renamed to `kestra.ee.mail-service`.");
    }

    private static LegacyConfigurationChecker checker(final Set<String> configuredKeys, final LegacyConfiguration... legacyConfigurations) {
        Environment environment = mock(Environment.class);
        when(environment.containsProperty(anyString())).thenAnswer(invocation -> configuredKeys.contains(invocation.getArgument(0, String.class)));
        when(environment.containsProperties(anyString())).thenAnswer(invocation -> configuredKeys.contains(invocation.getArgument(0, String.class)));

        return new LegacyConfigurationChecker(environment, List.of(() -> List.of(legacyConfigurations)));
    }
}
