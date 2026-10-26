package io.kestra.core.runners.pebble.filters;

import java.util.Arrays;
import java.util.HashMap;
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
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void nonStringType() {
        assertThatThrownBy(() -> variableRenderer.render("{{ 'Hello' | escapeChar(1) }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("expects the value of 'type' to be either 'single', 'double', or 'shell'");
    }

    @Test
    void mapWithNullValue() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "it's");
        payload.put("nick", null);

        String render = variableRenderer.render(
            "{{ payload | escapeChar('shell') | toJson(includeNulls=true) }}",
            Map.of("payload", payload)
        );

        assertThat(JacksonMapper.toMap(render))
            .containsEntry("name", "it'\\''s")
            .containsEntry("nick", null);
    }

    @Test
    void listWithNullItem() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ items | escapeChar('shell') | toJson }}",
            Map.of("items", Arrays.asList("it's", null, Arrays.asList("x", null)))
        );

        assertThat(render).isEqualTo("[\"it'\\\\''s\",null,[\"x\",null]]");
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
