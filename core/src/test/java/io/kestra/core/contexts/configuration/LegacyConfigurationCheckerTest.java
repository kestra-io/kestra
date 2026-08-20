package io.kestra.core.contexts.configuration;

import java.util.List;
import java.util.Set;

import io.kestra.core.contexts.configuration.LegacyConfiguration.Severity;
import io.kestra.core.exceptions.KestraRuntimeException;

import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

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
            .hasMessageContaining("`micronaut.security.login.failed-attempts` has been renamed to `kestra.security.login.failed-attempts`.")
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
            .hasMessageContaining("`kestra.webserver.google-analytics` has been removed.");
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
            .hasMessageContaining("`kestra.jdbc.cleaner` has been removed.");
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
