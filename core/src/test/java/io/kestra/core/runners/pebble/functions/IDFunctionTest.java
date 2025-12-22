package io.kestra.core.runners.pebble.functions;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Collections;
import org.junit.jupiter.api.Test;

@MicronautTest
class IDFunctionTest {
    @Inject VariableRenderer variableRenderer;

    @Test
    void checkIdIsNotEmpty() throws IllegalVariableEvaluationException {
        String rendered =
            variableRenderer.render(
                "{{ id() }}", Collections.emptyMap());
        assertThat(!rendered.isEmpty()).as(rendered).isTrue();
    }
}
