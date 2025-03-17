package io.kestra.plugin.core.kv;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

@KestraTest
class GetKeysTest {
    static final String TEST_KEY_PREFIX_TEST = "test";

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldGetAllKeys() throws Exception {
        // Given
        String namespace = IdUtils.create();
        RunContext runContext = this.runContextFactory.of(Map.of(
            "flow", Map.of("namespace", namespace)
        ));

        GetKeys getKeys = GetKeys.builder()
            .id(GetKeys.class.getSimpleName())
            .type(GetKeys.class.getName())
            .build();

        final KVStore kv = runContext.namespaceKv(namespace);
        kv.put("test-key", new KVValueAndMetadata(null, "value"));
        kv.put("test-second-key", new KVValueAndMetadata(null, "value"));
        kv.put("another-key", new KVValueAndMetadata(null, "value"));

        // When
        GetKeys.Output run = getKeys.run(runContext);

        // Then
        assertThat(run.getKeys(), containsInAnyOrder("test-key", "test-second-key", "another-key"));
    }

    @Test
    void shouldGetKeysGivenMatchingPrefix() throws Exception {
        // Given
        String namespace = IdUtils.create();
        RunContext runContext = this.runContextFactory.of(Map.of(
            "flow", Map.of("namespace", namespace),
            "inputs", Map.of(
                "prefix", TEST_KEY_PREFIX_TEST
            )
        ));

        GetKeys getKeys = GetKeys.builder()
            .id(GetKeys.class.getSimpleName())
            .type(GetKeys.class.getName())
            .prefix(new Property<>("{{ inputs.prefix }}"))
            .build();

        final KVStore kv = runContext.namespaceKv(namespace);
        kv.put(TEST_KEY_PREFIX_TEST + "-key", new KVValueAndMetadata(null, "value"));
        kv.put(TEST_KEY_PREFIX_TEST + "-second-key", new KVValueAndMetadata(null, "value"));
        kv.put("another-key", new KVValueAndMetadata(null, "value"));

        // When
        GetKeys.Output run = getKeys.run(runContext);

        // Then
        assertThat(run.getKeys(), containsInAnyOrder(TEST_KEY_PREFIX_TEST + "-key", TEST_KEY_PREFIX_TEST + "-second-key"));
    }

    @Test
    void shouldGetNoKeysGivenEmptyKeyStore() throws Exception {
        // Given
        String namespace = IdUtils.create();
        RunContext runContext = this.runContextFactory.of(Map.of(
            "flow", Map.of("namespace", namespace),
            "inputs", Map.of(
                "prefix", TEST_KEY_PREFIX_TEST
            )
        ));

        GetKeys getKeys = GetKeys.builder()
            .id(GetKeys.class.getSimpleName())
            .type(GetKeys.class.getName())
            .prefix(new Property<>("{{ inputs.prefix }}"))
            .build();

        // When
        GetKeys.Output run = getKeys.run(runContext);

        // Then
        assertThat(run.getKeys(), empty());
    }
}
