package io.kestra.core.runners.pebble.functions;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void shouldThrowWhenRangeUpperLimitIsLongMax() {
        assertThatThrownBy(
            () -> variableRenderer.render("{{ range(0, 9223372036854775807) }}", Collections.emptyMap())
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("cannot produce more than");
    }

    @Test
    void shouldThrowWhenRangeLowerLimitIsLongMin() {
        assertThatThrownBy(
            () -> variableRenderer.render("{{ range(start, 0) }}", Map.of("start", Long.MIN_VALUE))
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("cannot produce more than");
    }

    @Test
    void shouldThrowWhenRangeSpansFullLongDomain() {
        assertThatThrownBy(
            () -> variableRenderer.render("{{ range(start, end) }}", Map.of("start", Long.MIN_VALUE, "end", Long.MAX_VALUE))
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("cannot produce more than");
    }

    @Test
    void shouldAllowRangeAtMaxSize() {
        assertThatCode(
            () -> variableRenderer.render("{{ range(1, 1000000) }}", Collections.emptyMap())
        ).doesNotThrowAnyException();
    }

    @Test
    void shouldThrowWhenRangeIsJustAboveMaxSize() {
        assertThatThrownBy(
            () -> variableRenderer.render("{{ range(1, 1000001) }}", Collections.emptyMap())
        ).isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("cannot produce more than");
    }
}
