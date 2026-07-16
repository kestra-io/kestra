package io.kestra.jdbc;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the persisted JSON key for the (renamed) {@code revision} field of metadata stored in the
 * JDBC JSONB {@code value} column. The Java field is named {@code revision}, but the serialized key
 * MUST stay {@code "version"}: the DDL generated columns read {@code CAST(value ->> 'version' ...)}
 * and renaming the persisted key would force a JSONB backfill across H2/Postgres/MySQL on every
 * existing install. Uses {@link JdbcMapper#of()} — the exact mapper that writes/reads the column.
 */
class VersionJsonKeyFreezeTest {
    @Test
    void persistedKvMetadataKeepsVersionJsonKey() throws JacksonException {
        PersistedKvMetadata metadata = PersistedKvMetadata.builder()
            .namespace("my.ns")
            .name("my-key")
            .revision(7)
            .build();

        JsonNode node = JdbcMapper.of().readTree(JdbcMapper.of().writeValueAsString(metadata));

        assertThat(node.has("version")).isTrue();
        assertThat(node.get("version").asInt()).isEqualTo(7);
        assertThat(node.has("revision")).isFalse();
    }

    @Test
    void persistedKvMetadataReadsLegacyVersionJsonKey() throws JacksonException {
        String legacy = "{\"namespace\":\"my.ns\",\"name\":\"my-key\",\"version\":7,\"last\":true,\"deleted\":false}";

        PersistedKvMetadata metadata = JdbcMapper.of().readValue(legacy, PersistedKvMetadata.class);

        assertThat(metadata.getRevision()).isEqualTo(7);
    }

    @Test
    void namespaceFileMetadataKeepsVersionJsonKey() throws JacksonException {
        NamespaceFileMetadata metadata = NamespaceFileMetadata.builder()
            .namespace("my.ns")
            .path("/sub/file.txt")
            .revision(7)
            .size(12L)
            .build();

        JsonNode node = JdbcMapper.of().readTree(JdbcMapper.of().writeValueAsString(metadata));

        assertThat(node.has("version")).isTrue();
        assertThat(node.get("version").asInt()).isEqualTo(7);
        assertThat(node.has("revision")).isFalse();
    }

    @Test
    void namespaceFileMetadataReadsLegacyVersionJsonKey() throws JacksonException {
        String legacy = "{\"namespace\":\"my.ns\",\"path\":\"/sub/file.txt\",\"version\":7,\"size\":12,\"last\":true,\"deleted\":false}";

        NamespaceFileMetadata metadata = JdbcMapper.of().readValue(legacy, NamespaceFileMetadata.class);

        assertThat(metadata.getRevision()).isEqualTo(7);
    }
}
