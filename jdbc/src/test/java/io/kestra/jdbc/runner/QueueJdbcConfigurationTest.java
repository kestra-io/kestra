package io.kestra.jdbc.runner;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class QueueJdbcConfigurationTest {
    @Test
    void shouldCaptureArbitraryJdbcOptionsWhenBindingConfiguration() {
        // Given
        try (
            var context = ApplicationContext.builder()
                .deduceEnvironment(false)
                .properties(
                    Map.of(
                        "kestra.queue.jdbc.type", "h2",
                        "kestra.queue.jdbc.url", "jdbc:h2:mem:queue-config",
                        "kestra.queue.jdbc.maximum-pool-size", 3,
                        "kestra.queue.jdbc.data-source-properties.cache-prep-stmts", true
                    )
                )
                .start()
        ) {
            // When
            var configuration = context.getBean(QueueJdbcConfiguration.class);

            // Then
            assertThat(configuration.type()).contains("h2");
            assertThat(configuration.getJdbcConfig())
                .containsEntry("url", "jdbc:h2:mem:queue-config")
                .containsEntry("maximumPoolSize", 3)
                .containsEntry("dataSourceProperties", Map.of("cachePrepStmts", true));
        }
    }

    @Test
    void shouldReturnEmptyConfigurationWhenJdbcOptionsAreAbsent() {
        // Given
        var configuration = new QueueJdbcConfiguration(null);

        // When-Then
        assertThat(configuration.type()).isEmpty();
        assertThat(configuration.getJdbcConfig()).isEmpty();
    }
}
