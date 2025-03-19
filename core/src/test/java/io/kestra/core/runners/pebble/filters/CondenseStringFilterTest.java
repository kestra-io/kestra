package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import java.util.Map;
import java.util.stream.Stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;



@KestraTest
public class CondenseStringFilterTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void condenseValidStringTest() throws IllegalVariableEvaluationException {
        String exp = "{{ \"hello\n world\" | condense }}";
        String expected = "hello world";
        String render = variableRenderer.render(exp, Map.of());
        assertThat(render, is(expected));
    }

    @Test
    void condenseInvalidStringTest() throws IllegalVariableEvaluationException {
        String exp = "{{ 12 | condense }}";
        IllegalVariableEvaluationException exception = assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render(exp, Map.of());
        });
        assertTrue(exception.getMessage().contains("condense can only be applied on strings"));
    }
}
