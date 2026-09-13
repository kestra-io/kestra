package io.kestra.core.runners.pebble.filters;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class EndsWithFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void out() throws IllegalVariableEvaluationException {
        Boolean render = Boolean.parseBoolean(
            variableRenderer.render("{{ \"Hello World\" | endsWith(\"World\") }}", Map.of())
        );

        assertThat(render).isTrue();
    }

    @Test
    void shouldReturnFalseWhenInputIsNull() throws IllegalVariableEvaluationException {
        // Given / When
        String render = variableRenderer.render("{{ null | endsWith('x') }}", Map.of());

        // Then
        assertThat(render).isEqualTo("false");
    }

    @Test
    void shouldThrowWhenValueArgumentIsNull() {
        // Given / When / Then
        assertThatThrownBy(() -> variableRenderer.render("{{ 'abc' | endsWith(value=null) }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("The argument 'value' is required");
    }
}