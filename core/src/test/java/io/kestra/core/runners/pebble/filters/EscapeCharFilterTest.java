package io.kestra.core.runners.pebble.filters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
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

        assertThat(render).isEqualTo(expected);
    }

    @Test
    void mapWithNullValue() throws IllegalVariableEvaluationException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("first", "hello");
        map.put("empty", null);

        String render = variableRenderer.render("{{ map | escapeChar('single') }}", Map.of("map", map));

        assertThat(render).isEqualTo("{\"first\":\"hello\"}");
    }

    @Test
    void listWithNullValue() throws IllegalVariableEvaluationException {
        List<Object> list = new ArrayList<>();
        list.add("hello");
        list.add(null);

        String render = variableRenderer.render("{{ list | escapeChar('single') }}", Map.of("list", list));

        assertThat(render).isEqualTo("[\"hello\"]");
    }

    @ParameterizedTest
    @ValueSource(strings = { "foo", "" })
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
            Arguments.of("single", "\"L'eau c'est la vie\"", "L\\'eau c\\'est la vie"), // codespell:ignore
            Arguments.of("double", "'\"Hello\"'", "\\\"Hello\\\""),
            Arguments.of("shell", "\"L'eau c'est la vie\"", "L'\\''eau c'\\''est la vie"), // codespell:ignore
            Arguments.of("single", "''", ""),
            Arguments.of("double", "''", ""),
            Arguments.of("shell", "''", "")
        );
    }
}
