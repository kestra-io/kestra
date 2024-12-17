package io.kestra.core.runners.pebble.functions;

import static org.hamcrest.MatcherAssert.assertThat;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import java.util.Collections;
import org.junit.jupiter.api.Test;

@KestraTest
class IDFunctionTest {
    @Inject VariableRenderer variableRenderer;

    @Test
    void checkIdIsNotEmpty() throws IllegalVariableEvaluationException {
        String rendered =
            variableRenderer.render(
                "{{ id() }}", Collections.emptyMap());
        assertThat(rendered, !rendered.isEmpty());
    }
}
