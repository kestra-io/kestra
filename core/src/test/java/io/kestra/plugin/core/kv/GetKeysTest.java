package io.kestra.plugin.core.kv;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class GetKeysTest {
    static final String TEST_KEY_PREFIX_TEST = "test";

    @Inject
    TestRunContextFactory runContextFactory;

    @Test
    void shouldGetAllKeys() throws Exception {
        // Given
        String namespace = IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespace);

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
        assertThat(run.getKeys()).containsExactlyInAnyOrder("test-key", "test-second-key", "another-key");
    }

    @Test
    void shouldGetKeysGivenMatchingPrefix() throws Exception {
        // Given
        String namespace = IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespace,
            Map.of("inputs", Map.of("prefix", TEST_KEY_PREFIX_TEST)));

        GetKeys getKeys = GetKeys.builder()
            .id(GetKeys.class.getSimpleName())
            .type(GetKeys.class.getName())
            .prefix(Property.ofExpression("{{ inputs.prefix }}"))
            .build();

        final KVStore kv = runContext.namespaceKv(namespace);
        kv.put(TEST_KEY_PREFIX_TEST + "-key", new KVValueAndMetadata(null, "value"));
        kv.put(TEST_KEY_PREFIX_TEST + "-second-key", new KVValueAndMetadata(null, "value"));
        kv.put("another-key", new KVValueAndMetadata(null, "value"));

        // When
        GetKeys.Output run = getKeys.run(runContext);

        // Then
        assertThat(run.getKeys()).containsExactlyInAnyOrder(TEST_KEY_PREFIX_TEST + "-key", TEST_KEY_PREFIX_TEST + "-second-key");
    }

    @Test
    void shouldGetNoKeysGivenEmptyKeyStore() throws Exception {
        // Given
        String namespace = IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespace,
            Map.of("inputs", Map.of("prefix", TEST_KEY_PREFIX_TEST)));

        GetKeys getKeys = GetKeys.builder()
            .id(GetKeys.class.getSimpleName())
            .type(GetKeys.class.getName())
            .prefix(Property.ofExpression("{{ inputs.prefix }}"))
            .build();

        // When
        GetKeys.Output run = getKeys.run(runContext);

        // Then
        assertThat(run.getKeys()).isEmpty();
    }
}
