package io.kestra.core.runners.pebble.functions;

import java.time.*;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest
class RenderFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldRenderForString() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", "test"));
        assertThat(rendered).isEqualTo("test");
    }

    @Test
    void shouldRenderForInteger() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", 42));
        assertThat(rendered).isEqualTo("42");
    }

    @Test
    void shouldRenderForLong() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", 42L));
        assertThat(rendered).isEqualTo("42");
    }

    @Test
    void shouldRenderForBoolean() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", true));
        assertThat(rendered).isEqualTo("true");
    }

    @Test
    void shouldRenderForNull() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", new HashMap<>() {
            {
                put("input", null);
            }
        });
        assertThat(rendered).isEqualTo("");
    }

    @Test
    void shouldRenderForDateTime() throws IllegalVariableEvaluationException {
        Instant now = Instant.now();
        LocalDateTime datetime = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", datetime));
        assertThat(rendered).isEqualTo(datetime.toString());
    }

    @Test
    void shouldRenderForDuration() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", Duration.ofSeconds(5)));
        assertThat(rendered).isEqualTo(Duration.ofSeconds(5).toString());
    }

    @Test
    void shouldThrowOnCircularRender() {
        assertThatThrownBy(() -> variableRenderer.render("{{ render(input) }}", Map.of("input", "{{ render(input) }}")))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Maximum render() nesting depth");
    }
}
