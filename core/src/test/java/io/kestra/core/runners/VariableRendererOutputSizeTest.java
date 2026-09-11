package io.kestra.core.runners;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
@Property(name = "kestra.variables.max-output-size", value = "1000")
class VariableRendererOutputSizeTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldThrowWhenRenderedOutputExceedsMaxSize() {
        // Given a loop whose accumulated output (~5000 chars) is far above the 1000-char limit,
        // no single write is oversized, so only a cumulative guard can stop it before OOM.
        String template = "{% for i in range(0, 500) %}xxxxxxxxxx{% endfor %}";

        // When / Then it fails gracefully as a validation error rather than exhausting the heap.
        assertThatThrownBy(() -> variableRenderer.render(template, Collections.emptyMap()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("maximum allowed size");
    }

    @Test
    void shouldRenderOutputUnderMaxSize() throws IllegalVariableEvaluationException {
        String result = variableRenderer.render("{% for i in range(0, 10) %}x{% endfor %}", Collections.emptyMap());
        assertThat(result).isEqualTo("xxxxxxxxxxx");
    }
}
