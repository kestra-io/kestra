package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@KestraTest
class RandomPortFunctionTest {
    @Inject VariableRenderer variableRenderer;

    @Test
    void checkIsDefined() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ randomPort() }}", Collections.emptyMap());
        assertThat(Integer.parseInt(rendered), greaterThanOrEqualTo(0));
    }
}
