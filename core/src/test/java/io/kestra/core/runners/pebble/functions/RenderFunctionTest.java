package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.HashMap;
import java.util.Map;

@KestraTest
class RenderFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldRenderForString() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", "test"));
        Assertions.assertEquals("test", rendered);
    }

    @Test
    void shouldRenderForInteger() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", 42));
        Assertions.assertEquals("42", rendered);
    }

    @Test
    void shouldRenderForLong() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", 42L));
        Assertions.assertEquals("42", rendered);
    }

    @Test
    void shouldRenderForBoolean() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", Map.of("input", true));
        Assertions.assertEquals("true", rendered);
    }

    @Test
    void shouldRenderForNull() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}", new HashMap<>(){{put("input", null);}});
        Assertions.assertEquals("", rendered);
    }

    @Test
    void shouldRenderForDateTime() throws IllegalVariableEvaluationException {
        Instant now = Instant.now();
        LocalDateTime datetime = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        String rendered = variableRenderer.render("{{ render(input) }}",  Map.of("input", datetime));
        Assertions.assertEquals(datetime.toString(), rendered);
    }

    @Test
    void shouldRenderForDuration() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ render(input) }}",  Map.of("input", Duration.ofSeconds(5)));
        Assertions.assertEquals(Duration.ofSeconds(5).toString(), rendered);
    }
}
