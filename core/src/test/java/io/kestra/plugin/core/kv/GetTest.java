package io.kestra.plugin.core.kv;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreValueWrapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
public class GetTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void defaultCase() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        String key = "my-key";
        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");

        Get get = Get.builder()
            .id(Get.class.getSimpleName())
            .type(Get.class.getName())
            .namespace("{{ inputs.namespace }}")
            .key("{{ inputs.key }}")
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, get, Map.of(
            "namespace", namespaceId,
            "key", key
        ));

        final KVStore kv = runContext.namespaceKv(namespaceId);
        kv.put(key, new KVStoreValueWrapper<>(null, value));

        Get.Output run = get.run(runContext);

        assertThat(run.getValue(), is(value));
    }

    @Test
    void nonPresentKey() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        Get get = Get.builder()
            .id(Get.class.getSimpleName())
            .type(Get.class.getName())
            .namespace(namespaceId)
            .key("my-key")
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, get, Collections.emptyMap());
        Get.Output run = get.run(runContext);

        assertThat(run.getValue(), nullValue());

        NoSuchElementException noSuchElementException = Assertions.assertThrows(NoSuchElementException.class, () -> get.toBuilder().errorOnMissing(true).build().run(runContext));
        assertThat(noSuchElementException.getMessage(), is("No value found for key 'my-key' in namespace '" + namespaceId + "' and `errorOnMissing` is set to true"));
    }
}
