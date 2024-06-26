package io.kestra.plugin.core.kv;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreValueWrapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
public class DeleteTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void defaultCase() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        String key = "my-key";

        Delete delete = Delete.builder()
            .id(Delete.class.getSimpleName())
            .type(Delete.class.getName())
            .namespace("{{ inputs.namespace }}")
            .key("{{ inputs.key }}")
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, delete, Map.of(
            "namespace", namespaceId,
            "key", key
        ));

        final KVStore kv = runContext.storage().namespaceKv(namespaceId);
        kv.put(key, new KVStoreValueWrapper<>(null, "value"));

        Delete.Output run = delete.run(runContext);

        assertThat(run.isDeleted(), is(true));
    }

    @Test
    void nonPresentKey() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        Delete delete = Delete.builder()
            .id(Delete.class.getSimpleName())
            .type(Delete.class.getName())
            .namespace(namespaceId)
            .key("my-key")
            .build();


        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, delete, Collections.emptyMap());
        Delete.Output run = delete.run(runContext);

        assertThat(run.isDeleted(), is(false));

        NoSuchElementException noSuchElementException = Assertions.assertThrows(NoSuchElementException.class, () -> delete.toBuilder().errorOnMissing(true).build().run(runContext));
        assertThat(noSuchElementException.getMessage(), is("No value found for key 'my-key' in namespace '" + namespaceId + "' and `errorOnMissing` is set to true"));
    }
}
