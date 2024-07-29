package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class FromJsonFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void jsonDecodeFunction() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ fromJson('{\"test1\": 1, \"test2\": 2, \"test3\": 3}').test1 }}", Map.of());
        assertThat(render, is("1"));

        render = variableRenderer.render("{{ fromJson('{\"test1\": [{\"test1\": 666}, 2, 3], \"test2\": 2, \"test3\": 3}').test1[0].test1 }}", Map.of());
        assertThat(render, is("666"));

        render = variableRenderer.render("{{ fromJson('[1, 2, 3]')[0] }}", Map.of());
        assertThat(render, is("1"));

        render = variableRenderer.render("{{ fromJson('{\"empty_object\":{}}') }}", Map.of());
        assertThat(render, is("{\"empty_object\":{}}"));

        render = variableRenderer.render("{{ fromJson(null) }}", Map.of());
        assertThat(render, emptyString());
    }

    @Test
    void exception() {
        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ fromJson() }}", Map.of()));

        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ fromJson('{not: json}') }}", Map.of()));
    }

    @Test
    void jsonFunction() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ json('{\"test1\": 1, \"test2\": 2, \"test3\": 3}').test1 }}", Map.of());
        assertThat(render, is("1"));
    }
}
