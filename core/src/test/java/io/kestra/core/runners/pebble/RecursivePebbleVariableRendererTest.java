package io.kestra.core.runners.pebble;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
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
            "third", "{{second}}",
            "map", ImmutableMap.of(
                "third", "{{third}}"
            ),
            "list", ImmutableList.of(
                "{{third}}"
            ),
            "set", ImmutableSet.of(
                "{{third}}"
            )
        );

        String render = variableRenderer.render("{{ third }}", vars);
        assertThat(render).isEqualTo("1");

        render = variableRenderer.render("{{ map }}", vars);
        assertThat(render).isEqualTo("{\"third\":\"1\"}");

        render = variableRenderer.render("{{ list }}", vars);
        assertThat(render).isEqualTo("[\"1\"]");

        render = variableRenderer.render("{{ set }}", vars);
        assertThat(render).isEqualTo("[\"1\"]");
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
        assertThat(illegalVariableEvaluationException.getMessage()).contains("Function or Macro [render] does not exist");
    }

    @Test
    void renderFunctionKeepRaw() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{% raw %}{{first}}{% endraw %}", Collections.emptyMap())).isEqualTo("{{first}}");
    }
}