package io.kestra.core.runners.pebble.functions;

import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariables;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class KvFunctionTest {

    @Inject
    private StorageInterface storageInterface;

    @Inject
    VariableRenderer variableRenderer;

    @BeforeEach
    void reset() throws IOException {
        storageInterface.deleteByPrefix(MAIN_TENANT, null, URI.create(StorageContext.kvPrefix("io.kestra.tests")));
    }

    @Test
    void shouldGetValueFromKVGivenExistingKey() throws IllegalVariableEvaluationException, IOException {
        // Given
        KVStore kv = new InternalKVStore(MAIN_TENANT, "io.kestra.tests", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = getVariables("io.kestra.tests");

        // When
        String rendered = variableRenderer.render("{{ kv('my-key') }}", variables);

        // Then
        assertThat(rendered).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    void shouldGetValueFromKVGivenExistingKeyWithInheritance() throws IllegalVariableEvaluationException, IOException {
        // Given
        KVStore kv = new InternalKVStore(MAIN_TENANT, "my.company", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        KVStore firstKv = new InternalKVStore(MAIN_TENANT, "my", storageInterface);
        firstKv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "firstValue")));

        Map<String, Object> variables = getVariables("my.company.team");

        // When
        String rendered = variableRenderer.render("{{ kv('my-key') }}", variables);

        // Then
        assertThat(rendered).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    void shouldNotGetValueFromKVWithGivenNamespaceAndInheritance() throws IOException {
        // Given
        KVStore kv = new InternalKVStore(MAIN_TENANT, "kv", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = getVariables("my.company.team");

        // When
        Assertions.assertThrows(IllegalVariableEvaluationException.class, () ->
            variableRenderer.render("{{ kv('my-key', namespace='kv.inherited') }}", variables));
    }

    @Test
    void shouldGetValueFromKVGivenExistingAndNamespace() throws IllegalVariableEvaluationException, IOException {
        // Given
        KVStore kv = new InternalKVStore(MAIN_TENANT, "kv", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = getVariables("io.kestra.tests");

        // When
        String rendered = variableRenderer.render("{{ kv('my-key', namespace='kv') }}", variables);

        // Then
        assertThat(rendered).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    void shouldGetEmptyGivenNonExistingKeyAndErrorOnMissingFalse() throws IllegalVariableEvaluationException {
        // Given
        Map<String, Object> variables = getVariables("io.kestra.tests");

        // When
        String rendered = variableRenderer.render("{{ kv('my-key', errorOnMissing=false) }}", variables);

        // Then
        assertThat(rendered).isEqualTo("");
    }

    @Test
    void shouldFailGivenNonExistingKeyAndErrorOnMissingTrue() {
        // Given
        Map<String, Object> variables = getVariables("io.kestra.tests");

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
        Map<String, Object> variables = getVariables("io.kestra.tests");
        // When
        IllegalVariableEvaluationException exception = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ kv('my-key') }}", variables);
        });

        // Then
        assertThat(exception.getMessage()).isEqualTo("io.pebbletemplates.pebble.error.PebbleException: The key 'my-key' does not exist in the namespace 'io.kestra.tests'. ({{ kv('my-key') }}:1)");
    }

}
