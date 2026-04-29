package io.kestra.core.runners.pebble.expression;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class LessThanExpressionTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldCompareStrings() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of("left", "a", "right", "b");

        assertThat(variableRenderer.render("{{ left < right }}", vars)).isEqualTo("true");
        assertThat(variableRenderer.render("{{ right < left }}", vars)).isEqualTo("false");
        assertThat(variableRenderer.render("{{ left < left }}", vars)).isEqualTo("false");
    }

    @Test
    void shouldCompareNumbers() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of("three", 3, "five", 5);

        assertThat(variableRenderer.render("{{ three < five }}", vars)).isEqualTo("true");
        assertThat(variableRenderer.render("{{ five < three }}", vars)).isEqualTo("false");
    }
}
