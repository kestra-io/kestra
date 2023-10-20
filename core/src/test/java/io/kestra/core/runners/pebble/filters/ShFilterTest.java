package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@MicronautTest
public class ShFilterTest {
    @Inject
    private VariableRenderer variableRenderer;

    @Test
    void bashShellFormat() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "echo '{{ shellVar | sh }}'",
            Map.of("shellVar", "foo' bar'baz")
        );

        assertThat(render, is("echo 'foo'\\'' bar'\\''baz'"));
    }
}
