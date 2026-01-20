package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class FromJsonFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void fronJsonFunction() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ fromJson('{\"test1\": 1, \"test2\": 2, \"test3\": 3}').test1 }}", Map.of());
        assertThat(render).isEqualTo("1");

        render = variableRenderer.render("{{ fromJson('{\"test1\": [{\"test1\": 666}, 2, 3], \"test2\": 2, \"test3\": 3}').test1[0].test1 }}", Map.of());
        assertThat(render).isEqualTo("666");

        render = variableRenderer.render("{{ fromJson('[1, 2, 3]')[0] }}", Map.of());
        assertThat(render).isEqualTo("1");

        render = variableRenderer.render("{{ fromJson('{\"empty_object\":{}}') }}", Map.of());
        assertThat(render).isEqualTo("{\"empty_object\":{}}");

        render = variableRenderer.render("{{ fromJson(null) }}", Map.of());
        assertThat(render).isEmpty();
    }

    @Test
    void exception() {
        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ fromJson() }}", Map.of()));

        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ fromJson('{not: json}') }}", Map.of()));
    }

    @Test
    void jsonFunction() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ json('{\"test1\": 1, \"test2\": 2, \"test3\": 3}').test1 }}", Map.of());
        assertThat(render).isEqualTo("1");
    }
}
