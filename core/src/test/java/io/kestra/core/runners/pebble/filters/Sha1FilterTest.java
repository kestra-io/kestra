package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class Sha1FilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void out() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ \"hello\" | sha1 }}", Map.of());

        assertThat(render.equals("hello"), is(false));
    }
}
