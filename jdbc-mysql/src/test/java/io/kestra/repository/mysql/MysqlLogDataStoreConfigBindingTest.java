package io.kestra.repository.mysql;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL counterpart of {@code PostgresLogDataStoreConfigBindingTest}: guards that the real
 * {@code kestra.logs.mysql.*} sub-config deserializes onto the store plugin. A plain unit test — no
 * Micronaut context.
 */
class MysqlLogDataStoreConfigBindingTest {

    @Test
    void shouldDeserializeDedicatedDatasourceConfigOntoTheStore() {
        // Given: a dedicated-logs-database config
        Map<String, Object> config = Map.of(
            "url", "jdbc:mysql://mysql-logs:3306/kestra_logs",
            "username", "kestra",
            "password", "k3str4",
            "table", "logs"
        );

        // When: it is deserialized onto the store plugin (the production path)
        MysqlLogDataStore store = JacksonMapper.toMap(config, MysqlLogDataStore.class);

        // Then: every dedicated-datasource key binds, with no Unrecognized-field error
        assertThat(store).isNotNull();
        assertThat(store.getUrl()).isEqualTo("jdbc:mysql://mysql-logs:3306/kestra_logs");
        assertThat(store.getUsername()).isEqualTo("kestra");
        assertThat(store.getPassword()).isEqualTo("k3str4");
        assertThat(store.getTableName()).isEqualTo("logs");
    }

    @Test
    void shouldDeserializeEmptyConfigForTheMainBackendFallback() {
        // Given: no kestra.logs.<type>.* keys (logs stay in the main DB)
        // When / Then: an empty map still binds cleanly, leaving the connection fields null
        MysqlLogDataStore store = JacksonMapper.toMap(Map.of(), MysqlLogDataStore.class);

        assertThat(store).isNotNull();
        assertThat(store.getUrl()).isNull();
        assertThat(store.getTableName()).isNull();
    }
}
