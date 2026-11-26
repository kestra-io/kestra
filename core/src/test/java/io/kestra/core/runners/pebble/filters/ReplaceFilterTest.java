package io.kestra.core.runners.pebble.filters;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.utils.RegexTestUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class ReplaceFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void string() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'john doe is john doe' | replace({'john': 'jane'}) }}", Map.of());

        assertThat(render).isEqualTo("jane doe is jane doe");
    }

    @Test
    void regexp() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'aa1bb2cc3dd4ee5' | replace({'(\\d)': '-$1-'}, regexp=true) }}", Map.of());

        assertThat(render).isEqualTo("aa-1-bb-2-cc-3-dd-4-ee-5-");
    }

    @Test
    void mapWithNullValue() throws IllegalVariableEvaluationException {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("first", "john");
        input.put("empty", null);

        Map<String, Object> replacePairs = new LinkedHashMap<>();
        replacePairs.put("john", "jane");
        replacePairs.put("unmatched", null);

        String render = variableRenderer.render(
            "{{ map | replace(pairs) }}",
            Map.of("map", input, "pairs", replacePairs)
        );

        assertThat(render).isEqualTo("{\"first\":\"jane\"}");
    }

    @Test
    void listWithNullValue() throws IllegalVariableEvaluationException {
        List<Object> input = new ArrayList<>();
        input.add("john");
        input.add(null);

        Map<String, Object> replacePairs = new LinkedHashMap<>();
        replacePairs.put("john", "jane");

        String render = variableRenderer.render(
            "{{ list | replace(pairs) }}",
            Map.of("list", input, "pairs", replacePairs)
        );

        assertThat(render).isEqualTo("[\"jane\"]");
    }

    @Test
    void regexpReDoSShouldTimeout() {
        try {
            RegexTestUtils.resetAndSetTimeout(Duration.ofMillis(200));
            String evilInput = "a".repeat(25) + "b";

            assertThatThrownBy(
                () -> variableRenderer.render(
                    "{{ '" + evilInput + "' | replace({'(.*a){25}': 'x'}, regexp=true) }}", Map.of()
                )
            )
                .isInstanceOf(IllegalVariableEvaluationException.class)
                .hasMessageContaining("timed out");
        } finally {
            RegexTestUtils.reset();
        }
    }
}
