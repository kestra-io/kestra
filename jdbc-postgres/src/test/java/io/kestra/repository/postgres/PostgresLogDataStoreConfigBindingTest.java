package io.kestra.repository.postgres;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the exact deserialization production performs in
 * {@code AbstractPluginInterfaceFactory.resolve()} → {@code JacksonMapper.toMap(config, cls)} with
 * the real {@code kestra.logs.postgres.*} sub-config. A plain unit test — no Micronaut context.
 */
class PostgresLogDataStoreConfigBindingTest {

    @Test
    void shouldDeserializeDedicatedDatasourceConfigOntoTheStore() {
        // Given: the documented dedicated-logs-database config (Notion "Example A")
        Map<String, Object> config = Map.of(
            "url", "jdbc:postgresql://postgres-logs:5432/kestra_logs",
            "username", "kestra",
            "password", "k3str4",
            "table", "logs"
        );

        // When: it is deserialized onto the store plugin (the production path)
        PostgresLogDataStore store = JacksonMapper.toMap(config, PostgresLogDataStore.class);

        // Then: every dedicated-datasource key binds, with no Unrecognized-field error
        assertThat(store).isNotNull();
        assertThat(store.getUrl()).isEqualTo("jdbc:postgresql://postgres-logs:5432/kestra_logs");
        assertThat(store.getUsername()).isEqualTo("kestra");
        assertThat(store.getPassword()).isEqualTo("k3str4");
        assertThat(store.getTableName()).isEqualTo("logs");
    }

    @Test
    void shouldDeserializeEmptyConfigForTheMainBackendFallback() {
        // Given: no kestra.logs.<type>.* keys (logs stay in the main DB)
        // When / Then: an empty map still binds cleanly, leaving the connection fields null
        PostgresLogDataStore store = JacksonMapper.toMap(Map.of(), PostgresLogDataStore.class);

        assertThat(store).isNotNull();
        assertThat(store.getUrl()).isNull();
        assertThat(store.getTableName()).isNull();
    }
}
