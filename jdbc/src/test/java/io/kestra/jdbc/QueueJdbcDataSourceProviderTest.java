package io.kestra.jdbc;

import java.util.Map;

import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.jdbc.runner.QueueJdbcConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QueueJdbcDataSourceProviderTest {

    private static QueueJdbcDataSourceProvider provider(QueueJdbcConfiguration config, String repositoryType) {
        return new QueueJdbcDataSourceProvider(
            config,
            new Settings(),
            null,
            null,
            new RepositoryConfiguration(repositoryType),
            ""
        );
    }

    private static QueueJdbcConfiguration config(Map<String, Object> map) {
        return new QueueJdbcConfiguration(map);
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

    @Test
    void shouldReusePrimaryDatasourceWhenNoDedicatedUrlIsConfigured() {
        // Given
        HikariDataSource primaryDataSource = mock(HikariDataSource.class);
        JooqDSLContextWrapper primaryWrapper = mock(JooqDSLContextWrapper.class);
        try (
            var provider = new QueueJdbcDataSourceProvider(
                config(Map.of("type", "h2")), new Settings(), primaryDataSource, primaryWrapper,
                new RepositoryConfiguration("h2"), ""
            )
        ) {
            // When-Then
            assertThat(provider.dataSource()).isSameAs(primaryDataSource);
            assertThat(provider.wrapper()).isSameAs(primaryWrapper);
            assertThat(provider.dedicatedWrapper()).isNull();
        }
        verify(primaryDataSource, never()).close();
    }

    @Test
    void shouldIgnoreDedicatedQueueDatabaseWhenRunningOnEphemeralDatabase() {
        // Given: retained queue configuration must never connect to the configured infrastructure.
        HikariDataSource primaryDataSource = mock(HikariDataSource.class);
        JooqDSLContextWrapper primaryWrapper = mock(JooqDSLContextWrapper.class);
        try (
            var provider = new QueueJdbcDataSourceProvider(
                config(
                    Map.of(
                        "type", "postgres",
                        "url", "jdbc:postgresql://postgres-queue:5432/kestra_queue",
                        "username", "kestra",
                        "table", "custom_queue_table"
                    )
                ),
                new Settings(), primaryDataSource, primaryWrapper, new RepositoryConfiguration("memory"),
                "jdbc:h2:mem:ephemeral-queue;DB_CLOSE_DELAY=-1"
            )
        ) {
            // When-Then
            assertThat(provider.isDedicated()).isFalse();
            assertThat(provider.dedicatedWrapper()).isNull();
            assertThat(provider.dataSource()).isSameAs(primaryDataSource);
            assertThat(provider.wrapper()).isSameAs(primaryWrapper);
            assertThat(provider.table()).isEqualTo("queues");
        }
        verify(primaryDataSource, never()).close();
    }

    @Test
    void shouldApplyPoolAndDriverOptionsWhenUsingDedicatedQueueDatabase() {
        // Given: the queue dialect can differ from the primary repository's dialect.
        HikariDataSource dataSource;
        try (
            var provider = provider(
                config(
                    Map.of(
                        "type", "h2",
                        "url", "jdbc:h2:mem:queue-provider-options",
                        "username", "sa",
                        "maximumPoolSize", 2,
                        "minimumIdle", 0,
                        "connectionTimeout", 5000,
                        "poolName", "custom-queue-pool",
                        "dataSourceProperties", Map.of("MODE", "PostgreSQL")
                    )
                ), "postgres"
            )
        ) {
            // When
            dataSource = (HikariDataSource) provider.dataSource();

            // Then
            assertThat(provider.isDedicated()).isTrue();
            assertThat(provider.wrapper()).isSameAs(provider.dedicatedWrapper());
            assertThat(provider.dialect()).isEqualTo(SQLDialect.H2);
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(2);
            assertThat(dataSource.getMinimumIdle()).isZero();
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(5000);
            assertThat(dataSource.getPoolName()).isEqualTo("custom-queue-pool");
            assertThat(dataSource.getDataSourceProperties()).containsEntry("MODE", "PostgreSQL");
            assertThat(dataSource.isClosed()).isFalse();
        }
        assertThat(dataSource.isClosed()).isTrue();
    }
}
