package io.kestra.core.runners.pebble.filters;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class SubstringFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void substringBefore() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringBefore('.') }}", Map.of());
        assertThat(render).isEqualTo("a");
    }

    @Test
    void substringAfter() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringAfter(separator='.') }}", Map.of());
        assertThat(render).isEqualTo("b.c");
    }

    @Test
    void substringBeforeLast() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringBeforeLast(separator='.') }}", Map.of());
        assertThat(render).isEqualTo("a.b");

        render = variableRenderer.render("{{ 'a.b.c' | substringBeforeLast('.') }}", Map.of());
        assertThat(render).isEqualTo("a.b");
    }

    @Test
    void substringAfterLast() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'a.b.c' | substringAfterLast('.') }}", Map.of());
        assertThat(render).isEqualTo("c");
    }

    @Test
    void shouldThrowPebbleExceptionWhenSeparatorIsNotAString() {
        List<String> filterNames = List.of("substringAfter", "substringBefore", "substringAfterLast", "substringBeforeLast");

        for (String filterName : filterNames) {
            String template = "{{ 'a1b1c' | " + filterName + "(separator=1) }}";
            var exception = assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render(template, Map.of()));
            assertThat(exception.getCause())
                .isInstanceOfSatisfying(PebbleException.class, pebbleException -> {
                    assertThat(pebbleException.getMessage()).contains("The '" + filterName + "' filter argument 'separator' must be a string.");
                    assertThat(pebbleException.getLineNumber()).isEqualTo(1);
                });
        }
    }
}
