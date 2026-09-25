package io.kestra.jdbc;

import java.util.Map;

import org.jooq.conf.Settings;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.jdbc.runner.QueueJdbcConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueJdbcDataSourceProviderTest {

    private static QueueJdbcDataSourceProvider provider(QueueJdbcConfiguration config, String repositoryType) {
        return new QueueJdbcDataSourceProvider(
            config,
            new Settings(),
            null,
            null,
            new RepositoryConfiguration(repositoryType)
        );
    }

    private static QueueJdbcConfiguration config(Map<String, Object> map) {
        return new QueueJdbcConfiguration(
            (String) map.get("type"),
            (String) map.get("url"),
            (String) map.get("username"),
            (String) map.get("password"),
            (String) map.get("table")
        );
    }

    @Test
    void shouldNotBeDedicatedWhenNoConfig() {
        // Given: no queue.jdbc configuration at all
        QueueJdbcDataSourceProvider provider = provider(config(Map.of()), "h2");

        // When-Then: shared datasource, no dedicated pool, no error
        assertThat(provider.isDedicated()).isFalse();
    }

    @Test
    void shouldNotBeDedicatedWhenTypeMatchesRepositoryAndNoUrl() {
        // Given: kestra.queue.jdbc.type=h2 with the main repository also h2, no url
        QueueJdbcDataSourceProvider provider = provider(config(Map.of("type", "h2")), "h2");

        // When-Then: shared datasource, no dedicated pool, no error
        assertThat(provider.isDedicated()).isFalse();
    }

    @Test
    void shouldTreatMemoryRepositoryAsH2() {
        // Given: kestra.queue.jdbc.type=h2 with an in-memory repository (also H2 dialect)
        QueueJdbcDataSourceProvider provider = provider(config(Map.of("type", "h2")), "memory");

        // When-Then: dialects match, no error
        assertThatCode(provider::isDedicated).doesNotThrowAnyException();
        assertThat(provider.isDedicated()).isFalse();
    }

    @Test
    void shouldFailFastWhenUrlIsConfiguredWithoutUsername() {
        // Given: a dedicated queue database URL but no username (credentials must be explicit)
        QueueJdbcDataSourceProvider provider = provider(
            config(
                Map.of(
                    "type", "postgres",
                    "url", "jdbc:postgresql://postgres-queue:5432/kestra_queue"
                )
            ), "postgres"
        );

        // When-Then: fail fast rather than silently connecting as an unintended user
        assertThatThrownBy(provider::isDedicated)
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("kestra.queue.jdbc.url")
            .hasMessageContaining("kestra.queue.jdbc.username")
            .hasMessageContaining("never inherited");
    }

    @Test
    void shouldFailFastWhenQueueTypeDiffersFromRepositoryAndNoUrl() {
        // Given: kestra.queue.jdbc.type=mysql but the main repository is h2, and no dedicated url
        QueueJdbcDataSourceProvider provider = provider(config(Map.of("type", "mysql")), "h2");

        // When-Then: fail fast with a clear, actionable message
        assertThatThrownBy(provider::isDedicated)
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("kestra.queue.jdbc.type=mysql")
            .hasMessageContaining("kestra.repository.type=h2")
            .hasMessageContaining("kestra.queue.jdbc.url");
    }

    @Test
    void shouldDefaultTableToQueuesWhenNotConfigured() {
        // Given: no table configured
        QueueJdbcDataSourceProvider provider = provider(config(Map.of()), "h2");

        // When-Then: default table name is "queues"
        assertThatCode(provider::table).doesNotThrowAnyException();
        assertThat(provider.table()).isEqualTo("queues");
    }

    @Test
    void shouldReturnConfiguredTableWhenSet() {
        // Given: a custom table name configured
        QueueJdbcDataSourceProvider provider = provider(config(Map.of("table", "custom_queue_table")), "h2");

        // When-Then: custom table name is returned
        assertThat(provider.table()).isEqualTo("custom_queue_table");
    }
}
