package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class RandomPortFunctionTest {
    @Inject VariableRenderer variableRenderer;

    @Test
    void checkIsDefined() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ randomPort() }}", Collections.emptyMap());
        assertThat(Integer.parseInt(rendered)).isGreaterThanOrEqualTo(0);
    }
}
