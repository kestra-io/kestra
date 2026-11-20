package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class ReadKvEntryToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai.kv.read";
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private ReadKvEntryTool tool;

    @Inject
    private KVStoreService kvStoreService;

    @Test
    void shouldReturnTypedValueWithMetadataWhenEntryExists() throws IOException {
        String key = newKey();
        put(key, Map.of("hello", "world"), new KVMetadata("greeting", (Instant) null));

        Map<String, Object> result = ToolCalls.call(tool, "read_kv_entry", arguments(key), MAIN_TENANT);

        assertThat(result)
            .containsEntry("namespace", NAMESPACE)
            .containsEntry("key", key)
            .containsEntry("type", "JSON")
            .containsEntry("value", Map.of("hello", "world"))
            .containsEntry("truncated", false)
            .containsEntry("revision", 1)
            .containsEntry("description", "greeting");
    }

    @Test
    void shouldTruncateValueWhenLongerThanMaxValueLength() throws IOException {
        String key = newKey();
        put(key, "x".repeat(ReadKvEntryTool.DEFAULT_MAX_VALUE_LENGTH + 5000), new KVMetadata(null, (Instant) null));

        Map<String, Object> byDefault = ToolCalls.call(tool, "read_kv_entry", arguments(key), MAIN_TENANT);
        Map<String, Object> custom = ToolCalls.call(tool, "read_kv_entry", arguments(key, "\"maxValueLength\": 100"), MAIN_TENANT);

        assertThat(byDefault).containsEntry("truncated", true).containsEntry("type", "STRING");
        assertThat((String) byDefault.get("value")).hasSize(ReadKvEntryTool.DEFAULT_MAX_VALUE_LENGTH + 1).endsWith("…");
        assertThat((String) custom.get("value")).hasSize(101).endsWith("…");
    }

    @Test
    void shouldFailWhenEntryHasExpired() throws IOException {
        String key = newKey();
        put(key, "stale", new KVMetadata(null, Instant.now().minus(Duration.ofMinutes(5))));

        assertThatThrownBy(() -> tool.readKvEntry(NAMESPACE, key, null, CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("has expired");
    }

    @Test
    void shouldFailWhenEntryIsMissing() {
        assertThatThrownBy(() -> tool.readKvEntry(NAMESPACE, "does-not-exist", null, CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void shouldRejectMaxValueLengthWhenBelowOne() {
        assertThatThrownBy(() -> tool.readKvEntry(NAMESPACE, "any", 0, CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxValueLength must be 1 or greater");
    }

    private void put(final String key, final Object value, final KVMetadata metadata) throws IOException {
        kvStoreService.get(MAIN_TENANT, NAMESPACE).put(key, new KVValueAndMetadata(metadata, value));
    }

    private static String arguments(final String key, final String... extra) {
        return """
            { "namespace": "%s", "key": "%s"%s }""".formatted(NAMESPACE, key, extra.length == 0 ? "" : ", " + String.join(", ", extra));
    }

    private static String newKey() {
        return "key-" + IdUtils.create().toLowerCase();
    }
}
