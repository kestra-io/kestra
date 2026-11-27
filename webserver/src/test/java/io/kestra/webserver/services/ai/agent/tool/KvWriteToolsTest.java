package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class KvWriteToolsTest {
    private static final String NAMESPACE = "io.kestra.test.ai.kv.write";

    @Inject
    private CreateKvEntryTool createKvEntryTool;

    @Inject
    private UpdateKvEntryTool updateKvEntryTool;

    @Inject
    private DeleteKvEntryTool deleteKvEntryTool;

    @Inject
    private KVStoreService kvStoreService;

    @Test
    void shouldStoreTypedValueWithMetadataWhenCreating() throws IOException, ResourceExpiredException {
        String key = newKey();

        Map<String, Object> result = create(key, "{\"hello\": \"world\"}", "\"description\": \"greeting\", \"ttl\": \"PT1H\"");

        assertThat(result).containsEntry("key", key).containsEntry("type", "JSON").containsEntry("revision", 1);
        assertThat(store().getValue(key)).get().extracting(KVValue::value).isEqualTo(Map.of("hello", "world"));
        assertThat(entry(key).description()).isEqualTo("greeting");
        assertThat(entry(key).expirationDate()).isBetween(Instant.now().plus(Duration.ofMinutes(59)), Instant.now().plus(Duration.ofMinutes(61)));
    }

    @Test
    void shouldRefuseToCreateWhenKeyExists() throws IOException, ResourceExpiredException {
        String key = newKey();
        create(key, "first");

        assertThatThrownBy(() -> create(key, "overwritten"))
            .hasMessageContaining(key)
            .hasMessageContaining("update_kv_entry");
        assertThat(store().getValue(key)).get().extracting(KVValue::value).isEqualTo("first");
    }

    @Test
    void shouldRejectTtlWhenNotADuration() throws IOException {
        String key = newKey();

        assertThatThrownBy(() -> create(key, "value", "\"ttl\": \"1 hour\""))
            .hasMessageContaining("ISO-8601 duration");
        assertThat(store().get(key)).isEmpty();
    }

    @Test
    void shouldRejectTtlWhenNotGreaterThanZeroAndKeepTheExistingEntry() throws IOException, ResourceExpiredException {
        String key = newKey();
        create(key, "42", "\"ttl\": \"PT1H\"");

        assertThatThrownBy(() -> create(newKey(), "value", "\"ttl\": \"PT0S\""))
            .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> update(key, "43", "\"ttl\": \"PT0S\""))
            .hasMessageContaining("greater than zero");
        assertThat(store().getValue(key)).get().extracting(KVValue::value).isEqualTo(42);
    }

    @Test
    void shouldKeepDescriptionAndExpirationWhenUpdatingWithoutThem() throws IOException, ResourceExpiredException {
        String key = newKey();
        create(key, "42", "\"description\": \"a number\", \"ttl\": \"PT1H\"");
        KVEntry before = entry(key);

        Map<String, Object> result = update(key, "hello world");

        assertThat(result).containsEntry("type", "STRING").containsEntry("revision", 2);
        assertThat(store().getValue(key)).get().extracting(KVValue::value).isEqualTo("hello world");
        assertThat(entry(key).description()).isEqualTo("a number");
        assertThat(entry(key).expirationDate()).isEqualTo(before.expirationDate());
    }

    @Test
    void shouldReplaceDescriptionAndExpirationWhenUpdatingWithThem() throws IOException {
        String key = newKey();
        create(key, "42", "\"description\": \"a number\", \"ttl\": \"PT1H\"");

        update(key, "43", "\"description\": \"another number\", \"ttl\": \"P7D\"");

        assertThat(entry(key).description()).isEqualTo("another number");
        assertThat(entry(key).expirationDate()).isAfter(Instant.now().plus(Duration.ofDays(6)));
    }

    @Test
    void shouldRefuseToUpdateWhenKeyIsMissing() throws IOException {
        String key = newKey();

        assertThatThrownBy(() -> update(key, "value"))
            .hasMessageContaining("not found");
        assertThat(store().get(key)).isEmpty();
    }

    @Test
    void shouldRemoveEntryWhenDeleting() throws IOException {
        String key = newKey();
        create(key, "first");
        update(key, "second");

        Map<String, Object> result = ToolCalls.call(deleteKvEntryTool, "delete_kv_entry", identity(key), MAIN_TENANT);

        assertThat(result).containsEntry("key", key).containsEntry("revision", 2);
        assertThat(store().get(key)).isEmpty();
    }

    @Test
    void shouldFailWhenDeletingMissingEntry() {
        assertThatThrownBy(() -> ToolCalls.call(deleteKvEntryTool, "delete_kv_entry", identity("missing"), MAIN_TENANT))
            .hasMessageContaining("not found");
    }

    private Map<String, Object> create(final String key, final String value, final String... extra) {
        return ToolCalls.call(createKvEntryTool, "create_kv_entry", arguments(key, value, extra), MAIN_TENANT);
    }

    private Map<String, Object> update(final String key, final String value, final String... extra) {
        return ToolCalls.call(updateKvEntryTool, "update_kv_entry", arguments(key, value, extra), MAIN_TENANT);
    }

    private KVStore store() {
        return kvStoreService.get(MAIN_TENANT, NAMESPACE);
    }

    private KVEntry entry(final String key) throws IOException {
        return store().get(key).orElseThrow();
    }

    private static String arguments(final String key, final String value, final String... extra) {
        String escaped = value.replace("\"", "\\\"");
        return """
            { "namespace": "%s", "key": "%s", "value": "%s"%s }""".formatted(NAMESPACE, key, escaped, extra.length == 0 ? "" : ", " + String.join(", ", extra));
    }

    private static String identity(final String key) {
        return """
            { "namespace": "%s", "key": "%s" }""".formatted(NAMESPACE, key);
    }

    private static String newKey() {
        return "key-" + IdUtils.create().toLowerCase();
    }
}
