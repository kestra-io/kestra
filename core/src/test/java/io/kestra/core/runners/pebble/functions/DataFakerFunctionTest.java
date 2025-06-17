package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@KestraTest
class DataFakerFunctionTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldRenderGivenExpression() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ datafaker('#{Address.country}') }}", Map.of());
        assertThat(render).isNotNull();
    }

    @Test
    void shouldRenderGivenExpressionWithParameters() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ datafaker('#{Date.birthday \"2\",\"4\"}') }}", Map.of());
        assertThat(render).isNotNull();
    }

    @Test
    void shouldRenderGivenExpressionWithLocale() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ datafaker(expr='#{Address.country}', locale=['fr', 'FR']) }}", Map.of());
        assertThat(render).isNotNull();
    }

    @Test
    void shouldThrowOnInvalidLocaleFormat() {
        assertThatThrownBy(() ->
            variableRenderer.render("{{ datafaker(expr='#{Address.country}', locale=['en', 'US', 'NY', 'extra']) }}", Map.of())
        )
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Invalid value for argument 'locale'");
    }

    @Test
    void shouldThrowOnMissingExpression() {
        assertThatThrownBy(() ->
            variableRenderer.render("{{ datafaker()}}", Map.of())
        )
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Missing or invalid 'expr' argument");
    }
}