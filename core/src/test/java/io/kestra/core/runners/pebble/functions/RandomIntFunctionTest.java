package io.kestra.core.runners.pebble.functions;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class RandomIntFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void missingParameter() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{randomInt(lower)}}", Map.of("lower", 10L))
        );

        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{randomInt(upper)}}", Map.of("upper", 1L))
        );
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{randomInt()}}", Collections.emptyMap())
        );
    }

    @Test
    void testGenerateNumberPositive() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render(
            "{{ randomInt(lower, upper) }}", Map.of("lower", 1L, "upper", 10L)
        );
        assertThat(Long.parseLong(rendered) >= 1L && Long.parseLong(rendered) <= 10L).as(rendered).isTrue();
    }

    @Test
    void testGenerateNumberPositiveString() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ randomInt(lower, upper) }}", Map.of("lower", "1", "upper", "10"))
        );
    }

    @Test
    void testGenerateNumberUpperLessThanLower() {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render(
                "{{ randomInt(lower, upper) }}", Map.of("lower", 10L, "upper", 1L)
            )
        );
    }

    @Test
    void testGenerateNumberNegative() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render(
            "{{ randomInt(lower, upper) }}", Map.of("lower", -10L, "upper", -1L)
        );
        assertThat(Long.parseLong(rendered) >= -10L && Long.parseLong(rendered) <= -1L).as(rendered).isTrue();
    }

    @Test
    void testGenerateNumberSame() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render(
            "{{ randomInt(lower, upper) }}", Map.of("lower", 10L, "upper", 10L)
        );
        assertThat(Long.parseLong(rendered) == 10).as(rendered).isTrue();
    }

    @Test
    void shouldStayWithinBoundsWhenBoundsExceedIntRange() throws IllegalVariableEvaluationException {
        for (int i = 0; i < 100; i++) {
            String rendered = variableRenderer.render(
                "{{ randomInt(lower, upper) }}", Map.of("lower", 3000000000L, "upper", 3000000010L)
            );
            assertThat(Long.parseLong(rendered)).as(rendered).isBetween(3000000000L, 3000000009L);
        }

        boolean beyondInt = false;
        for (int i = 0; i < 100; i++) {
            String rendered = variableRenderer.render(
                "{{ randomInt(lower, upper) }}", Map.of("lower", 0L, "upper", 5000000000L)
            );
            long value = Long.parseLong(rendered);
            assertThat(value).as(rendered).isBetween(0L, 4999999999L);
            beyondInt |= value > Integer.MAX_VALUE;
        }
        assertThat(beyondInt).isTrue();
    }
}
