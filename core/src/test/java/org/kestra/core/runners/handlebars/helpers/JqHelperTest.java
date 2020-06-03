package org.kestra.core.runners.handlebars.helpers;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.runners.VariableRenderer;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class JqHelperTest {
    private final static VariableRenderer VARIABLE_RENDERER = new VariableRenderer();

    @Test
    void simple() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "first", ImmutableMap.of("second", ImmutableMap.of("third", "{{third}}")),
            "end", "awesome",
            "third", "{{end}}"
        );

        String render = VARIABLE_RENDERER.render("{{ jq first \".second.third\" }}", vars);

        assertThat(render, is("[\"awesome\"]"));
    }

    @Test
    void map() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", ImmutableMap.of(
                "a", 1,
                "b", 2,
                "c", 3
            ))
        );

        String render = VARIABLE_RENDERER.render("{{ jq vars \".second.a\"}}", vars);
        assertThat(render, is("[1]"));


        render = VARIABLE_RENDERER.render("{{ jq vars \".second.a\" true}}", vars);
        assertThat(render, is("1"));
    }

    @Test
    void list() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", Arrays.asList(1, 2, 3))
        );

        String render = VARIABLE_RENDERER.render("{{ jq vars \".second[]\"}}", vars);
        assertThat(render, is("[1,2,3]"));
    }
}
