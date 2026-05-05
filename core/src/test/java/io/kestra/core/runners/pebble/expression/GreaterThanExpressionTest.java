package io.kestra.core.runners.pebble.expression;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class GreaterThanExpressionTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldCompareStrings() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of("left", "b", "right", "a");

        assertThat(variableRenderer.render("{{ left > right }}", vars)).isEqualTo("true");
        assertThat(variableRenderer.render("{{ right > left }}", vars)).isEqualTo("false");
        assertThat(variableRenderer.render("{{ left > left }}", vars)).isEqualTo("false");
    }

    @Test
    void shouldCompareNumbers() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of("five", 5, "three", 3);

        assertThat(variableRenderer.render("{{ five > three }}", vars)).isEqualTo("true");
        assertThat(variableRenderer.render("{{ three > five }}", vars)).isEqualTo("false");
    }
}
