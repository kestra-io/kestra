package io.kestra.jdbc;

import java.util.Map;

import org.jooq.conf.Settings;
import org.junit.jupiter.api.Test;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.repositories.log.LogsConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogJdbcDataSourceProviderTest {

    private static LogJdbcDataSourceProvider provider(Map<String, Object> logs, String repositoryType) {
        return new LogJdbcDataSourceProvider(
            new LogsConfig(logs),
            new Settings(),
            null,
            new RepositoryConfiguration(repositoryType)
        );
    }

    @Test
    void shouldNotBeDedicatedWhenLogTypeMatchesRepositoryAndNoUrl() {
        // Given: kestra.logs.type=h2 with the main repository also h2, no url
        LogJdbcDataSourceProvider provider = provider(Map.of("type", "h2"), "h2");

        // When-Then: shared datasource, no dedicated pool, no error
        assertThat(provider.isDedicated()).isFalse();
    }

    @Test
    void shouldTreatMemoryRepositoryAsH2() {
        // Given: kestra.logs.type=h2 with an in-memory repository (also H2 dialect)
        LogJdbcDataSourceProvider provider = provider(Map.of("type", "h2"), "memory");

        // When-Then: dialects match, no error
        assertThatCode(provider::isDedicated).doesNotThrowAnyException();
        assertThat(provider.isDedicated()).isFalse();
    }

    // Note: the dedicated happy-path (url + username actually building a Hikari pool) needs a JDBC
    // driver on the classpath, which this dialect-agnostic module doesn't have — it is covered by
    // H2LogDataStoreDedicatedTest in :jdbc-h2 instead.

    @Test
    void shouldFailFastWhenUrlIsConfiguredWithoutUsername() {
        // Given: a dedicated logs database URL but no username (credentials must be explicit)
        LogJdbcDataSourceProvider provider = provider(
            Map.of(
                "type", "postgres", "postgres", Map.of(
                    "url", "jdbc:postgresql://postgres-logs:5432/kestra_logs"
                )
            ),
            "postgres"
        );

        // When-Then: fail fast rather than silently connecting as an unintended user
        assertThatThrownBy(provider::isDedicated)
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("kestra.logs.postgres.url")
            .hasMessageContaining("kestra.logs.postgres.username")
            .hasMessageContaining("never inherited");
    }

    @Test
    void shouldFailFastWhenLogTypeDiffersFromRepositoryAndNoUrl() {
        // Given: kestra.logs.type=mysql but the main repository is h2, and no dedicated url
        LogJdbcDataSourceProvider provider = provider(Map.of("type", "mysql"), "h2");

        // When-Then: fail fast with a clear, actionable message
        assertThatThrownBy(provider::isDedicated)
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("kestra.logs.type=mysql")
            .hasMessageContaining("kestra.repository.type=h2")
            .hasMessageContaining("kestra.logs.mysql.url");
    }
}
