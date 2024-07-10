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
class JsonDecodeFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void jsonDecodeFunction() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ jsonDecode('{\"test1\": 1, \"test2\": 2, \"test3\": 3}').test1 }}", Map.of());
        assertThat(render, is("1"));

        render = variableRenderer.render("{{ jsonDecode('{\"test1\": [{\"test1\": 666}, 2, 3], \"test2\": 2, \"test3\": 3}').test1[0].test1 }}", Map.of());
        assertThat(render, is("666"));

        render = variableRenderer.render("{{ jsonDecode('[1, 2, 3]')[0] }}", Map.of());
        assertThat(render, is("1"));

        render = variableRenderer.render("{{ jsonDecode('{\"empty_object\":{}}') }}", Map.of());
        assertThat(render, is("{\"empty_object\":{}}"));

        render = variableRenderer.render("{{ jsonDecode(null) }}", Map.of());
        assertThat(render, emptyString());
    }

    @Test
    void exception() {
        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ jsonDecode() }}", Map.of()));

        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ jsonDecode('{not: json}') }}", Map.of()));
    }

    @Test
    void jsonFunction() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ json('{\"test1\": 1, \"test2\": 2, \"test3\": 3}').test1 }}", Map.of());
        assertThat(render, is("1"));
    }
}
