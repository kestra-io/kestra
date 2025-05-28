package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class StringFilterTest {

    @Inject
    VariableRenderer variableRenderer;

    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("{{ 12.3 | string | className }}", String.class.getName()),
            Arguments.of("{{ {\"field\":\"hello\"} | string | className }}", String.class.getName())
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void run(String exp, String expected) throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(exp, Map.of());
        assertThat(render).isEqualTo(expected);
    }
}
