package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

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
}
