package io.kestra.core.runners.pebble;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.utils.Rethrow;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@Property(name = "kestra.variables.recursive-rendering", value = "true")
class RecursivePebbleVariableRendererTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void recursive() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "first", "1",
            "second", "{{first}}",
            "third", "{{second}}"
        );

        String render = variableRenderer.render("{{ third }}", vars);
        assertThat(render, is("1"));
    }

    @Test
    void renderFunctionNotInjectedIfRecursiveSettingsTrue() {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "first", "1"
        );

        IllegalVariableEvaluationException illegalVariableEvaluationException = assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ render(first) }}", vars)
        );
        assertThat(illegalVariableEvaluationException.getMessage(), containsString("Function or Macro [render] does not exist"));
    }
}