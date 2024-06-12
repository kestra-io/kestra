package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class EscapeCharFilterTest {
    @Inject
    private VariableRenderer variableRenderer;

    @ParameterizedTest
    @MethodSource("provideValidTypes")
    void validTypes(String type, String input, String expected) throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ " + input + " | escapeChar('" + type + "') }}",
            Map.of()
        );

        assertThat(render, is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", ""})
    void invalidTypes(String type) {
        assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render(
                "{{ 'Hello' | escapeChar('" + type + "') }}",
                Map.of()
            )
        );
    }

    private static Stream<Arguments> provideValidTypes() {
        return Stream.of(
            Arguments.of("single", "\"L'eau c'est la vie\"", "L\\'eau c\\'est la vie"),
            Arguments.of("double", "'\"Hello\"'", "\\\"Hello\\\""),
            Arguments.of("shell", "\"L'eau c'est la vie\"", "L'\\''eau c'\\''est la vie"),
            Arguments.of("single", "''", ""),
            Arguments.of("double", "''", ""),
            Arguments.of("shell", "''", "")
        );
    }
}
