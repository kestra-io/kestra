package io.kestra.plugin.core.kv;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
public class SetTest {
    static final String TEST_KEY = "test-key";

    @Inject
    StorageInterface storageInterface;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldSetKVGivenNoNamespace() throws Exception {
        // Given
        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key("{{ inputs.key }}")
            .value("{{ inputs.value }}")
            .build();

        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");
        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Map.of(
            "key", TEST_KEY,
            "value", value
        ));

        // When
        set.run(runContext);

        // Then
        final KVStore kv = runContext.namespaceKv(runContext.flowInfo().namespace());
        assertThat(kv.getValue(TEST_KEY), is(Optional.of(new KVValue(value))));
        assertThat(kv.list().getFirst().expirationDate(), nullValue());
    }

    @Test
    void shouldSetKVGivenSameNamespace() throws Exception {
        // Given
        RunContext runContext = this.runContextFactory.of(Map.of(
            "flow", Map.of("namespace", "io.kestra.test"),
            "inputs", Map.of(
                "key", TEST_KEY,
                "value", "test-value"
            )
        ));

        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key("{{ inputs.key }}")
            .value("{{ inputs.value }}")
            .namespace("io.kestra.test")
            .build();

        // When
        set.run(runContext);

        // Then
        final KVStore kv = runContext.namespaceKv("io.kestra.test");
        assertThat(kv.getValue(TEST_KEY), is(Optional.of(new KVValue("test-value"))));
        assertThat(kv.list().getFirst().expirationDate(), nullValue());
    }

    @Test
    void shouldSetKVGivenChildNamespace() throws Exception {
        // Given
        RunContext runContext = this.runContextFactory.of(Map.of(
            "flow", Map.of("namespace", "io.kestra.test"),
            "inputs", Map.of(
                "key", TEST_KEY,
                "value", "test-value"
            )
        ));

        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key("{{ inputs.key }}")
            .value("{{ inputs.value }}")
            .namespace("io.kestra.test.unit")
            .build();
        // When
        set.run(runContext);

        // then
        final KVStore kv = runContext.namespaceKv("io.kestra.test.unit");
        assertThat(kv.getValue(TEST_KEY), is(Optional.of(new KVValue("test-value"))));
        assertThat(kv.list().getFirst().expirationDate(), nullValue());
    }

    @Test
    void shouldFailGivenNonExistingNamespace() {
        // Given
        RunContext runContext = this.runContextFactory.of(Map.of(
            "flow", Map.of("namespace", "io.kestra.test"),
            "inputs", Map.of(
                "key", TEST_KEY,
                "value", "test-value"
            )
        ));

        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key("{{ inputs.key }}")
            .value("{{ inputs.value }}")
            .namespace("???")
            .build();

        // When - Then
        Assertions.assertThrows(KVStoreException.class, () -> set.run(runContext));
    }

    @Test
    void shouldSetKVGivenTTL() throws Exception {
        // Given
        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key("{{ inputs.key }}")
            .value("{{ inputs.value }}")
            .ttl(Duration.ofMinutes(5))
            .build();

        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");
        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Map.of(
            "key", TEST_KEY,
            "value", value
        ));

        // When
        set.run(runContext);

        // Then
        final KVStore kv = runContext.namespaceKv(runContext.flowInfo().namespace());
        assertThat(kv.getValue(TEST_KEY), is(Optional.of(new KVValue(value))));
        Instant expirationDate = kv.get(TEST_KEY).get().expirationDate();
        assertThat(expirationDate.isAfter(Instant.now().plus(Duration.ofMinutes(4))) && expirationDate.isBefore(Instant.now().plus(Duration.ofMinutes(6))), is(true));
    }

    @Test
    void shouldFailGivenExistingKeyAndOverwriteFalse() {
        // Given
        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key("{{ inputs.key }}")
            .value("{{ inputs.value }}")
            .overwrite(false)
            .build();

        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");
        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Map.of(
            "key", TEST_KEY,
            "value", value
        ));

        // When - Then
        KVStoreException exception = Assertions.assertThrows(KVStoreException.class, () -> set.run(runContext));
        assertThat(exception.getMessage(), is("Cannot set value for key '" + TEST_KEY + "'. Key already exists and `overwrite` is set to `false`."));
    }
}
