package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariables;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class KvFunctionTest {
    @Inject
    private KvMetadataRepositoryInterface kvMetadataRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldGetValueFromKVGivenExistingKey() throws IllegalVariableEvaluationException, IOException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        KVStore kv = new InternalKVStore(tenant, "io.kestra.tests", storageInterface, kvMetadataRepository);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = getVariables(tenant, "io.kestra.tests");

        // When
        String rendered = variableRenderer.render("{{ kv('my-key') }}", variables);

        // Then
        assertThat(rendered).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    void shouldGetValueFromKVGivenExistingKeyWithInheritance() throws IllegalVariableEvaluationException, IOException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        KVStore kv = new InternalKVStore(tenant, "my.company", storageInterface, kvMetadataRepository);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        KVStore firstKv = new InternalKVStore(tenant, "my", storageInterface, kvMetadataRepository);
        firstKv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "firstValue")));

        Map<String, Object> variables = getVariables(tenant, "my.company.team");

        // When
        String rendered = variableRenderer.render("{{ kv('my-key') }}", variables);

        // Then
        assertThat(rendered).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    void shouldNotGetValueFromKVWithGivenNamespaceAndInheritance() throws IOException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        KVStore kv = new InternalKVStore(tenant, "kv", storageInterface, kvMetadataRepository);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = getVariables(tenant, "my.company.team");

        // When
        Assertions.assertThrows(IllegalVariableEvaluationException.class, () ->
            variableRenderer.render("{{ kv('my-key', namespace='kv.inherited') }}", variables));
    }

    @Test
    void shouldGetValueFromKVGivenExistingAndNamespace() throws IllegalVariableEvaluationException, IOException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        KVStore kv = new InternalKVStore(tenant, "kv", storageInterface, kvMetadataRepository);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = getVariables(tenant, "io.kestra.tests");

        // When
        String rendered = variableRenderer.render("{{ kv('my-key', namespace='kv') }}", variables);

        // Then
        assertThat(rendered).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    void shouldGetEmptyGivenNonExistingKeyAndErrorOnMissingFalse() throws IllegalVariableEvaluationException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Map<String, Object> variables = getVariables(tenant, "io.kestra.tests");

        // When
        String rendered = variableRenderer.render("{{ kv('my-key', errorOnMissing=false) }}", variables);

        // Then
        assertThat(rendered).isEqualTo("");
    }

    @Test
    void shouldThrowOrGetEmptyIfExpiredDependingOnErrorOnMissing() throws IOException, IllegalVariableEvaluationException {
        String tenant = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        Map<String, Object> variables = getVariables(tenant, namespace);

        KVStore kv = new InternalKVStore(tenant, namespace, storageInterface, kvMetadataRepository);
        kv.put("my-expired-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().minus(1, ChronoUnit.HOURS)), "anyValue"));

        String rendered = variableRenderer.render("{{ kv('my-expired-key', errorOnMissing=false) }}", variables);
        assertThat(rendered).isEqualTo("");

        kv.put("another-expired-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().minus(1, ChronoUnit.HOURS)), "anyValue"));

        IllegalVariableEvaluationException exception = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ kv('another-expired-key') }}", variables));

        assertThat(exception.getMessage()).isEqualTo("io.pebbletemplates.pebble.error.PebbleException: The requested value has expired ({{ kv('another-expired-key') }}:1)");
    }

    @Test
    void shouldFailGivenNonExistingKeyAndErrorOnMissingTrue() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Map<String, Object> variables = getVariables(tenant, "io.kestra.tests");

        // When
        IllegalVariableEvaluationException exception = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ kv('my-key', errorOnMissing=true) }}", variables);
        });

        // Then
        assertThat(exception.getMessage()).isEqualTo("io.pebbletemplates.pebble.error.PebbleException: The key 'my-key' does not exist in the namespace 'io.kestra.tests'. ({{ kv('my-key', errorOnMissing=true) }}:1)");
    }

    @Test
    void shouldFailGivenNonExistingKeyUsingDefaults() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Map<String, Object> variables = getVariables(tenant, "io.kestra.tests");
        // When
        IllegalVariableEvaluationException exception = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ kv('my-key') }}", variables));

        // Then
        assertThat(exception.getMessage()).isEqualTo("io.pebbletemplates.pebble.error.PebbleException: The key 'my-key' does not exist in the namespace 'io.kestra.tests'. ({{ kv('my-key') }}:1)");
    }

}
