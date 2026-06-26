package io.kestra.core.storages.kv;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the serialized JSON key for the {@code revision} component of {@link KVEntry}, returned by
 * the KV list REST endpoint (GET /kv). Unlike the persisted metadata classes, {@link KVEntry} is a
 * pure REST DTO (never stored), so the 2.0 rename moves its key to {@code "revision"} with no
 * migration cost, matching the sibling GET /kv/{key} endpoint. This was a breaking change: the
 * legacy {@code "version"} key is no longer emitted.
 */
class KVEntryRevisionJsonKeyTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void serializesRevisionJsonKey() throws JsonProcessingException {
        KVEntry entry = new KVEntry("my.ns", "my-key", 7, "desc", Instant.now(), Instant.now(), null);

        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(entry));

        assertThat(node.has("revision")).isTrue();
        assertThat(node.get("revision").asInt()).isEqualTo(7);
        assertThat(node.has("version")).isFalse();
    }
}
