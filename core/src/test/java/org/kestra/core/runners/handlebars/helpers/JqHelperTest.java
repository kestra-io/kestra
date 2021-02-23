package org.kestra.core.runners.handlebars.helpers;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.runners.VariableRenderer;

import java.util.Arrays;

import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


class JqHelperTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void simple() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "first", ImmutableMap.of("second", ImmutableMap.of("third", "{{third}}")),
            "end", "awesome",
            "third", "{{end}}"
        );

        String render = variableRenderer.render("{{ jq first \".second.third\" }}", vars);

        assertThat(render, is("[\"awesome\"]"));
    }

    @Test
    void map() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", ImmutableMap.of(
                "a", 1,
                "b", 2,
                "c", 3,
                "d", "4"
            ))
        );

        String render = variableRenderer.render("{{ jq vars \".second.a\"}}", vars);
        assertThat(render, is("[1]"));


        render = variableRenderer.render("{{ jq vars \".second.a\" true}}", vars);
        assertThat(render, is("1"));

        render = variableRenderer.render("{{ jq vars \".second.d\" true}}", vars);
        assertThat(render, is("4"));
    }

    @Test
    void list() throws IllegalVariableEvaluationException {
        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", Arrays.asList(1, 2, 3))
        );

        String render = variableRenderer.render("{{ jq vars \".second[]\"}}", vars);
        assertThat(render, is("[1,2,3]"));
    }
}
