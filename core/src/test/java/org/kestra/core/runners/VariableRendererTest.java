package org.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class VariableRendererTest {
    private final static VariableRenderer VARIABLE_RENDERER = new VariableRenderer();

    @SuppressWarnings("unchecked")
    @Test
    void map() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> in = ImmutableMap.of(
            "string", "{{test}}",
            "list", Arrays.asList(
                "{{test}}",
                "{{test2}}"
            ),
            "int", 1
        );

        ImmutableMap<String, Object> vars = ImmutableMap.of("test", "top", "test2", "awesome");

        Map<String, Object> render = VARIABLE_RENDERER.render(in, vars);

        assertThat(render.get("string"), is("top"));
        assertThat((List<String>) render.get("list"), containsInAnyOrder("top", "awesome"));
        assertThat(render.get("int"), is(1));
    }
}
