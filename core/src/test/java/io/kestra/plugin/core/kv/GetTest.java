package io.kestra.plugin.core.kv;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class GetTest {

    static final String TEST_KV_KEY = "test-key";

    @Inject
    TestRunContextFactory runContextFactory;

    @Test
    void shouldGetGivenExistingKey() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of("inputs", Map.of(
                "key", TEST_KV_KEY,
                "namespace", namespaceId
            )
        ));

        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");

        Get get = Get.builder()
            .id(Get.class.getSimpleName())
            .type(Get.class.getName())
            .namespace(Property.ofExpression("{{ inputs.namespace }}"))
            .key(Property.ofExpression("{{ inputs.key }}"))
            .build();


        final KVStore kv = runContext.namespaceKv(namespaceId);

        // When
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(null, value));

        // Then
        Get.Output run = get.run(runContext);
        assertThat(run.getValue()).isEqualTo(value);
    }

    @Test
    void shouldGetGivenExistingKeyWithInheritance() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of(
            "inputs", Map.of(
                "key", TEST_KV_KEY
            )
        ));

        var value = Map.of("date", Instant.now().truncatedTo(ChronoUnit.MILLIS), "int", 1, "string", "string");

        Get get = Get.builder()
            .id(Get.class.getSimpleName())
            .type(Get.class.getName())
            .key(Property.ofExpression("{{ inputs.key }}"))
            .build();


        final KVStore kv = runContext.namespaceKv("io.kestra");

        // When
        kv.put(TEST_KV_KEY, new KVValueAndMetadata(null, value));

        // Then
        Get.Output run = get.run(runContext);
        assertThat(run.getValue()).isEqualTo(value);
    }

    @Test
    void shouldGetGivenNonExistingKey() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();
        RunContext runContext = this.runContextFactory.of(namespaceId, Map.of(
            "inputs", Map.of(
                "key", TEST_KV_KEY,
                "namespace", namespaceId
            )
        ));

        Get get = Get.builder()
            .id(Get.class.getSimpleName())
            .type(Get.class.getName())
            .namespace(Property.ofValue(namespaceId))
            .key(Property.ofValue("my-key"))
            .build();

        // When
        Get.Output run = get.run(runContext);

        // Then
        assertThat(run.getValue()).isNull();

        Get finalGet = get.toBuilder().errorOnMissing(Property.ofValue(true)).build();
        NoSuchElementException noSuchElementException = Assertions.assertThrows(NoSuchElementException.class, () -> finalGet.run(runContext));
        assertThat(noSuchElementException.getMessage()).isEqualTo("No value found for key 'my-key' in namespace '" + namespaceId + "' and `errorOnMissing` is set to true");
    }
}
