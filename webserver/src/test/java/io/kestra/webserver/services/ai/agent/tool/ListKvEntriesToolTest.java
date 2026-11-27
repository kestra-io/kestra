package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class ListKvEntriesToolTest {
    @Inject
    private ListKvEntriesTool tool;

    @Inject
    private KVStoreService kvStoreService;

    @Test
    void shouldReturnOnePageSortedByKeyWithTotalWhenFilteredByNamespace() throws IOException {
        // Given — more entries in the namespace than a page holds, plus entries in a child and in another namespace
        String tenant = "listkventries";
        String namespace = "io.kestra.test.ai.kv";
        for (int i = 0; i < 52; i++) {
            put(tenant, namespace, "key-%02d".formatted(i), "value " + i);
        }
        put(tenant, namespace + ".child", "child-key", "child");
        put(tenant, "io.kestra.test.ai.other", "other-key", "other");
        String filters = """
            "NAMESPACE": { "operator": "EQUALS", "value": "%s" }""".formatted(namespace);

        // When — the first and second pages are requested through the catalog
        Map<String, Object> first = ToolCalls.call(tool, "list_kv_entries", "{ %s }".formatted(filters), tenant);
        Map<String, Object> second = ToolCalls.call(tool, "list_kv_entries", "{ %s, \"page\": 2 }".formatted(filters), tenant);

        // Then — one page at a time, keys in order, only that namespace, values never included
        assertThat(((Number) first.get("total")).longValue()).isEqualTo(52);
        assertThat(first).containsEntry("page", 1);
        assertThat(keys(first)).hasSize(50).startsWith("key-00").endsWith("key-49");
        assertThat(keys(second)).containsExactly("key-50", "key-51");
        assertThat(((List<?>) first.get("entries")).getFirst()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("namespace", namespace)
            .containsEntry("revision", 1)
            .doesNotContainKey("value");
    }

    @Test
    void shouldRejectPageWhenBelowOne() {
        assertThatThrownBy(() -> tool.listKvEntries(List.of(), 0, AgentCallContext.Context.ofTenant("listkventriesbadpage")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Page must be 1 or greater");
    }

    private void put(final String tenant, final String namespace, final String key, final String value) throws IOException {
        kvStoreService.get(tenant, namespace).put(key, new KVValueAndMetadata(new KVMetadata(null, (Instant) null), value));
    }

    private static List<String> keys(final Map<String, Object> result) {
        return ((List<?>) result.get("entries")).stream()
            .map(entry -> (String) ((Map<?, ?>) entry).get("key"))
            .toList();
    }
}
