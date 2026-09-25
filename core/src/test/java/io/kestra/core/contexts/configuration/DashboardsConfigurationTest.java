package io.kestra.core.contexts.configuration;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class DashboardsConfigurationTest {
    @Inject
    private DashboardsConfiguration configuration;

    @Test
    void shouldDefaultToThirtySecondsCappedAtFiveMinutes() {
        assertThat(configuration.queryTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuration.maxQueryTimeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void shouldCapADashboardTimeoutAtTheMaximum() {
        assertThat(configuration.resolveQueryTimeout(null)).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuration.resolveQueryTimeout(Duration.ofSeconds(10))).isEqualTo(Duration.ofSeconds(10));
        assertThat(configuration.resolveQueryTimeout(Duration.ofHours(1))).isEqualTo(Duration.ofMinutes(5));
    }
}
