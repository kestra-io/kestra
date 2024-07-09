package io.kestra.plugin.core.kv;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVStore;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
public class SetTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void defaultCase() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .namespace("{{ inputs.namespace }}")
            .key("{{ inputs.key }}")
            .value("{{ inputs.value }}")
            .build();

        String key = "my-key";
        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");
        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Map.of(
            "namespace", namespaceId,
            "key", key,
            "value", value
        ));
        set.run(runContext);

        final KVStore kv = runContext.namespaceKv(namespaceId);
        assertThat(kv.get(key).get(), is(value));
        assertThat(kv.list().get(0).expirationDate(), nullValue());
    }

    @Test
    void ttl() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        String key = "my-key";
        String value = "value";
        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .namespace(namespaceId)
            .key(key)
            .value(value)
            .ttl(Duration.ofMinutes(5))
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Collections.emptyMap());
        set.run(runContext);

        final KVStore kv = runContext.namespaceKv(namespaceId);
        assertThat(kv.get(key).get(), is(value));
        Instant expirationDate = kv.list().get(0).expirationDate();
        assertThat(expirationDate.isAfter(Instant.now().plus(Duration.ofMinutes(4))) && expirationDate.isBefore(Instant.now().plus(Duration.ofMinutes(6))), is(true));
    }

    @Test
    void dontAllowOverwriteIfFalse() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        String key = "my-key";
        String value = "value";
        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .namespace(namespaceId)
            .key(key)
            .value(value)
            .overwrite(false)
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Collections.emptyMap());
        set.run(runContext);

        IllegalStateException illegalStateException = Assertions.assertThrows(IllegalStateException.class, () -> set.run(runContext));
        assertThat(illegalStateException.getMessage(), is("Key already exists and overwrite is set to `false`"));
    }
}
