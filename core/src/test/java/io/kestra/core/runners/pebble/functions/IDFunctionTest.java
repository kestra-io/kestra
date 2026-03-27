package io.kestra.core.runners.pebble.functions;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class IDFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void checkIdIsNotEmpty() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render(
            "{{ id() }}", Collections.emptyMap()
        );
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
    }
}
