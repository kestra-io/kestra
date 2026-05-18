package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class LoopOutputsFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldExtractOutputKeyForAllIterations() throws IllegalVariableEvaluationException {
        // Given
        Map<String, Object> variables = Map.of(
            "outputs", Map.of(
                "myLoop", Map.of(
                    "outputs", List.of(
                        Map.of("item", Map.of("value", "a", "iteration", 1), "outputs", Map.of("uri", "s3://bucket/a")),
                        Map.of("item", Map.of("value", "b", "iteration", 2), "outputs", Map.of("uri", "s3://bucket/b")),
                        Map.of("item", Map.of("value", "c", "iteration", 3), "outputs", Map.of("uri", "s3://bucket/c"))
                    )
                )
            )
        );

        // When
        String result = variableRenderer.render("{{ loopOutputs(outputs.myLoop.outputs, 'uri') }}", variables);

        // Then
        assertThat(result).isEqualTo("[\"s3://bucket/a\",\"s3://bucket/b\",\"s3://bucket/c\"]");
    }

    @Test
    void shouldReturnNullForMissingKey() throws IllegalVariableEvaluationException {
        // Given
        Map<String, Object> variables = Map.of(
            "outputs", Map.of(
                "myLoop", Map.of(
                    "outputs", List.of(
                        Map.of("item", Map.of("value", "a", "iteration", 1), "outputs", Map.of("uri", "s3://bucket/a")),
                        Map.of("item", Map.of("value", "b", "iteration", 2), "outputs", Map.of("uri", "s3://bucket/b"))
                    )
                )
            )
        );

        // When
        String result = variableRenderer.render("{{ loopOutputs(outputs.myLoop.outputs, 'nonExistentKey') }}", variables);

        // Then
        assertThat(result).isEqualTo("[null,null]");
    }

    @Test
    void shouldPreservePositionalAlignmentForMalformedEntries() throws IllegalVariableEvaluationException {
        // Given — second entry has no "outputs" map; third entry is a non-map value
        List<Object> loopOutputs = new ArrayList<>();
        loopOutputs.add(Map.of("item", Map.of("value", "a", "iteration", 1), "outputs", Map.of("uri", "s3://bucket/a")));
        loopOutputs.add(Map.of("item", Map.of("value", "b", "iteration", 2)));
        loopOutputs.add("not-a-map");

        Map<String, Object> variables = Map.of(
            "outputs", Map.of("myLoop", Map.of("outputs", loopOutputs))
        );

        // When
        String result = variableRenderer.render("{{ loopOutputs(outputs.myLoop.outputs, 'uri') }}", variables);

        // Then — result has an entry for every input element to preserve positional alignment
        assertThat(result).isEqualTo("[\"s3://bucket/a\",null,null]");
    }

    @Test
    void shouldReturnEmptyListForEmptyOutputs() throws IllegalVariableEvaluationException {
        // Given
        Map<String, Object> variables = Map.of(
            "outputs", Map.of(
                "myLoop", Map.of("outputs", List.of())
            )
        );

        // When
        String result = variableRenderer.render("{{ loopOutputs(outputs.myLoop.outputs, 'uri') }}", variables);

        // Then
        assertThat(result).isEqualTo("[]");
    }

    @Test
    void shouldThrowWhenOutputsArgumentIsMissing() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ loopOutputs() }}", Map.of())
        );
    }

    @Test
    void shouldThrowWhenKeyArgumentIsNull() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ loopOutputs([], null) }}", Map.of())
        );
    }

    @Test
    void shouldThrowWhenOutputsIsNotAList() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ loopOutputs('not-a-list', 'uri') }}", Map.of())
        );
    }
}
