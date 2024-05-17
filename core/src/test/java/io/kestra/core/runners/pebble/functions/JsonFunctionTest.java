package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class JsonFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void fromString() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ json('{\"test1\": 1, \"test2\": 2, \"test3\": 3}').test1 }}", Map.of());
        assertThat(render, is("1"));

        render = variableRenderer.render("{{ json('{\"test1\": [{\"test1\": 666}, 2, 3], \"test2\": 2, \"test3\": 3}').test1[0].test1 }}", Map.of());
        assertThat(render, is("666"));

        render = variableRenderer.render("{{ json('[1, 2, 3]')[0] }}", Map.of());
        assertThat(render, is("1"));

        render = variableRenderer.render("{{ json('{\"empty_object\":{}}') }}", Map.of());
        assertThat(render, is("{\"empty_object\":{}}"));
    }
}
