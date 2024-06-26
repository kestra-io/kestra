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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class GetKeysTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void defaultCase() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        String prefix = "my";
        GetKeys getKeys = GetKeys.builder()
            .id(GetKeys.class.getSimpleName())
            .type(GetKeys.class.getName())
            .namespace("{{ inputs.namespace }}")
            .prefix("{{ inputs.prefix }}")
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, getKeys, Map.of(
            "namespace", namespaceId,
            "prefix", prefix
        ));

        final KVStore kv = runContext.storage().namespaceKv(namespaceId);
        kv.put(prefix + "-key", new KVStoreValueWrapper<>(null, "value"));
        kv.put(prefix + "-second-key", new KVStoreValueWrapper<>(null, "value"));
        kv.put("another-key", new KVStoreValueWrapper<>(null, "value"));

        GetKeys.Output run = getKeys.run(runContext);

        assertThat(run.getKeys(), containsInAnyOrder("my-key", "my-second-key"));
    }

    @Test
    void noKeysReturnsEmptyList() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        GetKeys getKeys = GetKeys.builder()
            .id(GetKeys.class.getSimpleName())
            .type(GetKeys.class.getName())
            .namespace(namespaceId)
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, getKeys, Collections.emptyMap());
        GetKeys.Output run = getKeys.run(runContext);

        assertThat(run.getKeys(), empty());
    }
}
