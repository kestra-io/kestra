package io.kestra.plugin.core.kv;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class SetTest {

    @Inject
    TestRunContextFactory runContextFactory;

    @Test
    void shouldSetKVGivenNoNamespace() throws Exception {
        // Given
        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key(Property.ofExpression("{{ inputs.key }}"))
            .value(Property.ofExpression("{{ inputs.value }}"))
            .kvDescription(Property.ofExpression("{{ inputs.description }}"))
            .build();

        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");
        String description = "myDescription";
        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Map.of(
            "key", "no_ns_key",
            "value", value,
            "description", description
        ));

        // When
        set.run(runContext);

        // Then
        final KVStore kv = runContext.namespaceKv(runContext.flowInfo().namespace());
        Optional<KVValue> kvValueOptional = kv.getValue("no_ns_key");
        assertThat(kvValueOptional).isPresent().get().isEqualTo(new KVValue(value));
        Optional<KVEntry> noNsKey = kv.get("no_ns_key");
        assertThat(noNsKey).isPresent();
        KVEntry kvEntry = noNsKey.get();
        assertThat(kvEntry.expirationDate()).isNull();
        assertThat(kvEntry.description()).isEqualTo(description);
    }

    @Test
    void shouldSetKVGivenSameNamespace() throws Exception {
        // Given
        RunContext runContext = this.runContextFactory.of("io.kestra.test", Map.of(
            "inputs", Map.of(
                "key", "same_ns_key",
                "value", "test-value"
            )
        ));

        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key(Property.ofExpression("{{ inputs.key }}"))
            .value(Property.ofExpression("{{ inputs.value }}"))
            .namespace(Property.ofValue("io.kestra.test"))
            .build();

        // When
        set.run(runContext);

        // Then
        final KVStore kv = runContext.namespaceKv("io.kestra.test");
        assertThat(kv.getValue("same_ns_key")).isEqualTo(Optional.of(new KVValue("test-value")));
        assertThat(kv.list().getFirst().expirationDate()).isNull();
    }

    @Test
    void shouldSetKVGivenChildNamespace() throws Exception {
        // Given
        RunContext runContext = this.runContextFactory.of("io.kestra.test", Map.of(
            "inputs", Map.of(
                "key", "child_ns_key",
                "value", "test-value"
            )
        ));

        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key(Property.ofExpression("{{ inputs.key }}"))
            .value(Property.ofExpression("{{ inputs.value }}"))
            .namespace(Property.ofValue("io.kestra"))
            .build();
        // When
        set.run(runContext);

        // then
        final KVStore kv = runContext.namespaceKv("io.kestra");
        assertThat(kv.getValue("child_ns_key")).isEqualTo(Optional.of(new KVValue("test-value")));
        assertThat(kv.list().getFirst().expirationDate()).isNull();
    }

    @Test
    void shouldFailGivenNonExistingNamespace() {
        // Given
        RunContext runContext = this.runContextFactory.of("io.kestra.test", Map.of(
            "inputs", Map.of(
                "key", "non_existing_ns_key",
                "value", "test-value"
            )
        ));

        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key(Property.ofExpression("{{ inputs.key }}"))
            .value(Property.ofExpression("{{ inputs.value }}"))
            .namespace(Property.ofValue("not-found"))
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
            .key(Property.ofExpression("{{ inputs.key }}"))
            .value(Property.ofExpression("{{ inputs.value }}"))
            .ttl(Property.ofValue(Duration.ofMinutes(5)))
            .build();

        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");
        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Map.of(
            "key", "ttl_key",
            "value", value
        ));

        // When
        set.run(runContext);

        // Then
        final KVStore kv = runContext.namespaceKv(runContext.flowInfo().namespace());
        assertThat(kv.getValue("ttl_key")).isEqualTo(Optional.of(new KVValue(value)));
        Instant expirationDate = kv.get("ttl_key").get().expirationDate();
        assertThat(expirationDate.isAfter(Instant.now().plus(Duration.ofMinutes(4))) && expirationDate.isBefore(Instant.now().plus(Duration.ofMinutes(6)))).isTrue();
    }

    @FlakyTest
    @Test
    void shouldFailGivenExistingKeyAndOverwriteFalse() throws Exception {
        // Given
        String key = IdUtils.create();
        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key(Property.ofExpression("{{ inputs.key }}"))
            .value(Property.ofExpression("{{ inputs.value }}"))
            .overwrite(Property.ofValue(false))
            .build();

        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");
        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, Map.of(
            "key", key,
            "value", value
        ));

        // When - Then
        //set key a first:
        runContext.namespaceKv(runContext.flowInfo().namespace()).put(key, new KVValueAndMetadata(new KVMetadata("unused", (Instant)null), value));
        //fail because key is already set
        KVStoreException exception = Assertions.assertThrows(KVStoreException.class, () -> Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key(Property.ofExpression("{{ inputs.key }}"))
            .value(Property.ofExpression("{{ inputs.value }}"))
            .overwrite(Property.ofValue(false))
            .build().run(runContext));
        assertThat(exception.getMessage()).isEqualTo("Cannot set value for key '%s'. Key already exists and `overwrite` is set to `false`.".formatted(key));
    }

    @Test
    void typeSpecified() throws Exception {
        String key = "specified_key";
        KVStore kv = createAndPerformSetTask(key, "123.45", KVType.NUMBER);
        assertThat(kv.getValue(key).orElseThrow().value()).isEqualTo(123.45);

        kv = createAndPerformSetTask(key, "true", KVType.BOOLEAN);
        assertThat((Boolean) kv.getValue(key).orElseThrow().value()).isTrue();

        kv = createAndPerformSetTask(key, "2023-05-02T01:02:03Z", KVType.DATETIME);
        assertThat(kv.getValue(key).orElseThrow().value()).isEqualTo(Instant.parse("2023-05-02T01:02:03Z"));

        kv = createAndPerformSetTask(key, "P1DT5S", KVType.DURATION);
        assertThat(kv.getValue(key).orElseThrow().value()).isEqualTo(Duration.ofDays(1).plus(Duration.ofSeconds(5)));

        kv = createAndPerformSetTask(key, "[{\"some\":\"value\"},{\"another\":\"value\"}]", KVType.JSON);
        assertThat(kv.getValue(key).orElseThrow().value()).isEqualTo(List.of(Map.of("some", "value"), Map.of("another", "value")));

        kv = createAndPerformSetTask(key, "{{ 200 }}", KVType.STRING);
        assertThat(kv.getValue(key).orElseThrow().value()).isEqualTo("200");

        kv = createAndPerformSetTask(key, "{{ 200.1 }}", KVType.STRING);
        assertThat(kv.getValue(key).orElseThrow().value()).isEqualTo("200.1");
    }

    private KVStore createAndPerformSetTask(String key, String value, KVType type) throws Exception {
        Set set = Set.builder()
            .id(Set.class.getSimpleName())
            .type(Set.class.getName())
            .key(Property.ofValue(key))
            .value(value.contains("{{") ? Property.ofExpression(value) : Property.ofValue(value))
            .kvType(Property.ofValue(type))
            .build();
        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, set, null);
        set.run(runContext);
        return runContext.namespaceKv(runContext.flowInfo().namespace());
    }
}
