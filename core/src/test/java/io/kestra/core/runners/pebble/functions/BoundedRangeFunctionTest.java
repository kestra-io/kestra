package io.kestra.core.runners.pebble.functions;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class BoundedRangeFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldRenderNormalRange() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{{ range(1, 5) }}", Collections.emptyMap());
        assertThat(result).isEqualTo("[1,2,3,4,5]");
    }

    @Test
    void shouldThrowWhenRangeExceedsMaxSize() {
        assertThatThrownBy(
            () -> variableRenderer.render("{{ range(0, 2000000000) }}", Collections.emptyMap())
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("cannot produce more than");
    }

    @Test
    void shouldThrowWhenDescendingRangeExceedsMaxSize() {
        assertThatThrownBy(
            () -> variableRenderer.render("{{ range(2000000000, 0, -1) }}", Collections.emptyMap())
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("cannot produce more than");
    }
}
