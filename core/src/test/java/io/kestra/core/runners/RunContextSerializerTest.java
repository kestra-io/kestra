package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class RunContextSerializerTest {

    private static final Map<String, Object> TEST_VARIABLES = Map.of(
        "envs", Map.of("KEY", "VALUE"),
        "globals", Map.of("GLOBAL_KEY", "GLOBAL_VALUE"),
        "addSecretConsumer", "consumer"
    );

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldSerializeWithoutEnvs() throws JsonProcessingException {
        // Given
        RunContext runContext = runContextFactory.of(TEST_VARIABLES);
        runContext.setTraceParent("trace-parent-value");

        ObjectMapper mapper = JacksonMapper.ofJson();

        // When
        String json = mapper.writeValueAsString(runContext);
        Map<String, Object> deserialized = mapper.readValue(json, Map.class);

        // Then
        assertThat(deserialized).containsKey("variables");
        Map<String, Object> variables = (Map<String, Object>) deserialized.get("variables");

        // Verify that envs is filtered out
        assertThat(variables).doesNotContainKey("envs");

        // Verify that other keys are still present
        assertThat(deserialized).containsKey("traceParent");

        // Verify that globals, addSecretConsumer, and other keys are still present
        assertThat(variables).containsKey("globals");
        assertThat(variables).containsKey("addSecretConsumer");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveNullValuesInNestedVariablesMap() throws JsonProcessingException {
        // Given - top-level variables map is an ImmutableMap (no nulls), but nested maps can contain nulls
        HashMap<String, Object> inputs = new HashMap<>();
        inputs.put("input1", "value1");
        inputs.put("input2", null);

        Map<String, Object> variables = Map.of(
            "inputs", inputs,
            "globals", Map.of("GLOBAL_KEY", "GLOBAL_VALUE")
        );

        RunContext runContext = runContextFactory.of(variables);
        ObjectMapper mapper = JacksonMapper.ofJson();

        // When
        String json = mapper.writeValueAsString(runContext);
        Map<String, Object> deserialized = mapper.readValue(json, Map.class);

        // Then
        Map<String, Object> deserializedVars = (Map<String, Object>) deserialized.get("variables");
        Map<String, Object> deserializedInputs = (Map<String, Object>) deserializedVars.get("inputs");
        assertThat(deserializedInputs).containsEntry("input1", "value1");
        assertThat(deserializedInputs).containsEntry("input2", null);
    }
}
