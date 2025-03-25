package io.kestra.core.runners.pebble.functions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

@KestraTest(startRunner = true)
public class KvFunctionTest {

    @Inject
    private StorageInterface storageInterface;

    @Inject
    VariableRenderer variableRenderer;

    @BeforeEach
    void reset() throws IOException {
        storageInterface.deleteByPrefix(null, null, URI.create(StorageContext.kvPrefix("io.kestra.tests")));
    }

    @Test
    void shouldGetValueFromKVGivenExistingKey() throws IllegalVariableEvaluationException, IOException {
        // Given
        KVStore kv = new InternalKVStore(null, "io.kestra.tests", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "kv",
                "namespace", "io.kestra.tests")
        );

        // When
        String rendered = variableRenderer.render("{{ kv('my-key') }}", variables);

        // Then
        assertThat(rendered, is("{\"field\":\"value\"}"));
    }

    @Test
    void shouldGetValueFromKVGivenExistingKeyWithInheritance() throws IllegalVariableEvaluationException, IOException {
        // Given
        KVStore kv = new InternalKVStore(null, "my.company", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        KVStore firstKv = new InternalKVStore(null, "my", storageInterface);
        firstKv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "firstValue")));

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "kv",
                "namespace", "my.company.team")
        );

        // When
        String rendered = variableRenderer.render("{{ kv('my-key') }}", variables);

        // Then
        assertThat(rendered, is("{\"field\":\"value\"}"));
    }

    @Test
    void shouldNotGetValueFromKVWithGivenNamespaceAndInheritance() throws IOException {
        // Given
        KVStore kv = new InternalKVStore(null, "kv", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "kv",
                "namespace", "my.company.team")
        );

        // When
        Assertions.assertThrows(IllegalVariableEvaluationException.class, () ->
            variableRenderer.render("{{ kv('my-key', namespace='kv.inherited') }}", variables));
    }

    @Test
    void shouldGetValueFromKVGivenExistingAndNamespace() throws IllegalVariableEvaluationException, IOException {
        // Given
        KVStore kv = new InternalKVStore(null, "kv", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "kv",
                "namespace", "io.kestra.tests")
        );

        // When
        String rendered = variableRenderer.render("{{ kv('my-key', namespace='kv') }}", variables);

        // Then
        assertThat(rendered, is("{\"field\":\"value\"}"));
    }

    @Test
    void shouldGetEmptyGivenNonExistingKeyAndErrorOnMissingFalse() throws IllegalVariableEvaluationException {
        // Given
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "kv",
                "namespace", "io.kestra.tests")
        );

        // When
        String rendered = variableRenderer.render("{{ kv('my-key', errorOnMissing=false) }}", variables);

        // Then
        assertThat(rendered, is(""));
    }

    @Test
    void shouldFailGivenNonExistingKeyAndErrorOnMissingTrue() {
        // Given
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "kv",
                "namespace", "io.kestra.tests")
        );

        // When
        IllegalVariableEvaluationException exception = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ kv('my-key', errorOnMissing=true) }}", variables);
        });

        // Then
        assertThat(exception.getMessage(), is("io.pebbletemplates.pebble.error.PebbleException: The key 'my-key' does not exist in the namespace 'io.kestra.tests'. ({{ kv('my-key', errorOnMissing=true) }}:1)"));
    }

    @Test
    void shouldFailGivenNonExistingKeyUsingDefaults() {
        // Given
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", "kv",
                "namespace", "io.kestra.tests")
        );
        // When
        IllegalVariableEvaluationException exception = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ kv('my-key') }}", variables);
        });

        // Then
        assertThat(exception.getMessage(), is("io.pebbletemplates.pebble.error.PebbleException: The key 'my-key' does not exist in the namespace 'io.kestra.tests'. ({{ kv('my-key') }}:1)"));
    }
}
