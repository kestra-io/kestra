package io.kestra.plugin.core.kv;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PutTest {
    static final String TEST_KV_KEY = "test-key";

    @Inject
    TestRunContextFactory runContextFactory;

    @Test
    void shouldReplaceStringValue() throws Exception {
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of("inputs", Map.of(
            "key", TEST_KV_KEY,
            "namespace", namespaceId
        )));

        Put put = this.newPutTask(namespaceId, List.of(Map.of("value", "new-value")));

        final KVStore kv = runContext.namespaceKv(namespaceId);
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(new KVMetadata("my-description", (Instant) null), "old-value"));

        put.run(runContext);

        assertThat(kv.getValue(TEST_KV_KEY).orElseThrow().value()).isEqualTo("new-value");
        assertThat(kv.get(TEST_KV_KEY).orElseThrow().description()).isEqualTo("my-description");
    }

    @Test
    void shouldMergeJsonFromEntryList() throws Exception {
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of("inputs", Map.of(
            "key", TEST_KV_KEY,
            "namespace", namespaceId
        )));

        Put put = this.newPutTask(namespaceId, List.of(
            Map.of("key", "def", "value", 234),
            Map.of("key", "nested.b", "value", 2)
        ));

        final KVStore kv = runContext.namespaceKv(namespaceId);
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(null, Map.of(
            "abc", 123,
            "nested", Map.of("a", 1)
        )));

        put.run(runContext);

        assertThat(kv.getValue(TEST_KV_KEY).orElseThrow().value()).isEqualTo(Map.of(
            "abc", 123,
            "def", 234,
            "nested", Map.of("a", 1, "b", 2)
        ));
    }

    @Test
    void shouldMergeJsonFromMap() throws Exception {
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of("inputs", Map.of(
            "key", TEST_KV_KEY,
            "namespace", namespaceId
        )));

        Put put = this.newPutTask(namespaceId, Map.of(
            "def", 234,
            "nested", Map.of("b", 2)
        ));

        final KVStore kv = runContext.namespaceKv(namespaceId);
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(null, Map.of(
            "abc", 123,
            "nested", Map.of("a", 1)
        )));

        put.run(runContext);

        assertThat(kv.getValue(TEST_KV_KEY).orElseThrow().value()).isEqualTo(Map.of(
            "abc", 123,
            "def", 234,
            "nested", Map.of("a", 1, "b", 2)
        ));
    }

    @Test
    void shouldCreateJsonOnMissingKeyByDefault() throws Exception {
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of("inputs", Map.of(
            "key", TEST_KV_KEY,
            "namespace", namespaceId
        )));

        Put put = this.newPutTask(namespaceId, List.of(
            Map.of("key", "abc", "value", 123),
            Map.of("key", "nested.def", "value", 234)
        ));

        final KVStore kv = runContext.namespaceKv(namespaceId);

        put.run(runContext);

        assertThat(kv.getValue(TEST_KV_KEY).orElseThrow().value()).isEqualTo(Map.of(
            "abc", 123,
            "nested", Map.of("def", 234)
        ));
    }

    @Test
    void shouldFailWhenMissingKeyAndErrorOnMissing() {
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of("inputs", Map.of(
            "key", TEST_KV_KEY,
            "namespace", namespaceId
        )));

        Put put = this.newPutTask(namespaceId, List.of(Map.of("value", "new-value")))
            .toBuilder()
            .errorOnMissing(Property.ofValue(true))
            .build();

        NoSuchElementException exception = Assertions.assertThrows(NoSuchElementException.class, () -> put.run(runContext));
        assertThat(exception.getMessage()).isEqualTo("No value found for key '" + TEST_KV_KEY + "' in namespace '" + namespaceId + "' and `errorOnMissing` is set to true");
    }

    @Test
    void shouldFailWhenApplyingKeyedUpdateOnNonJsonValue() throws Exception {
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of("inputs", Map.of(
            "key", TEST_KV_KEY,
            "namespace", namespaceId
        )));

        Put put = this.newPutTask(namespaceId, List.of(
            Map.of("key", "abc", "value", 123)
        ));

        final KVStore kv = runContext.namespaceKv(namespaceId);
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(null, "plain-string"));

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> put.run(runContext));
        assertThat(exception.getMessage()).isEqualTo("Cannot apply keyed `value` updates to a non-JSON existing KV value.");
    }

    private Put newPutTask(String namespaceId, Object value) {
        return Put.builder()
            .id(Put.class.getSimpleName())
            .type(Put.class.getName())
            .namespace(Property.ofValue(namespaceId))
            .key(Property.ofValue(TEST_KV_KEY))
            .value(Property.ofValue(value))
            .build();
    }
}
