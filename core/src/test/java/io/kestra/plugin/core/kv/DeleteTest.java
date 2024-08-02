package io.kestra.plugin.core.kv;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class DeleteTest {
    static final String TEST_KV_KEY = "test-key";

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldOutputTrueGivenExistingKey() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(Map.of(
            "flow", Map.of("namespace", namespaceId),
            "inputs", Map.of(
                "key", TEST_KV_KEY,
                "namespace", namespaceId
            )
        ));

        Delete delete = Delete.builder()
            .id(Delete.class.getSimpleName())
            .type(Delete.class.getName())
            .namespace("{{ inputs.namespace }}")
            .key("{{ inputs.key }}")
            .build();

        final KVStore kv = runContext.namespaceKv(namespaceId);
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(null, "value"));

        // When
        Delete.Output run = delete.run(runContext);

        // Then
        assertThat(run.isDeleted(), is(true));
    }

    @Test
    void shouldOutputFalseGivenNonExistingKey() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(Map.of(
            "flow", Map.of("namespace", namespaceId),
            "inputs", Map.of(
                "key", TEST_KV_KEY,
                "namespace", namespaceId
            )
        ));

        Delete delete = Delete.builder()
            .id(Delete.class.getSimpleName())
            .type(Delete.class.getName())
            .namespace(namespaceId)
            .key("my-key")
            .build();

        // When
        Delete.Output run = delete.run(runContext);

        assertThat(run.isDeleted(), is(false));

        NoSuchElementException noSuchElementException = Assertions.assertThrows(NoSuchElementException.class, () -> delete.toBuilder().errorOnMissing(true).build().run(runContext));
        assertThat(noSuchElementException.getMessage(), is("No value found for key 'my-key' in namespace '" + namespaceId + "' and `errorOnMissing` is set to true"));
    }
}
