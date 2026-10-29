package io.kestra.core.runners.pebble;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class OverridableExpressionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldRenderOverrideOnlyForTheSameTopLevelExpression() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = Map.of(
            "code", "KR-0412 ",
            OverridableExpression.VARIABLE, Map.of("code", "KR-0413")
        );

        assertThat(variableRenderer.render("[{{code}}][{{ code | trim }}]{% if true %}[{{ code }}]{% endif %}", variables))
            .isEqualTo("[KR-0413][KR-0412][KR-0412 ]");
    }

    @Test
    void shouldRenderTypedOverrideOfExpressionWrittenWithOtherQuotes() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = Map.of(
            "outputs", Map.of("fetch", Map.of("delta", -12)),
            OverridableExpression.VARIABLE, Map.of("outputs.fetch.delta < 0", false)
        );

        assertThat(variableRenderer.renderTyped("{{ outputs[\"fetch\"].delta<0 }}", variables)).isEqualTo(false);
    }
}
