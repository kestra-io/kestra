package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class VariableRendererTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    VariableRenderer.VariableConfiguration variableConfiguration;

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldRenderUsingAlternativeRendering() throws IllegalVariableEvaluationException {
        TestVariableRenderer renderer = new TestVariableRenderer(applicationContext, variableConfiguration);
        String render = renderer.render("{{ dummy }}", Map.of());
        Assertions.assertEquals("result", render);
    }

    @Test
    void shouldRenderContactUntypedStringExpression() throws IllegalVariableEvaluationException {
        TestVariableRenderer renderer = new TestVariableRenderer(applicationContext, variableConfiguration);
        String render = renderer.render("{{ prefix }}.kestra.{{ suffix }}", Map.of("prefix", "io", "suffix", "unittest"));
        Assertions.assertEquals("io.kestra.unittest", render);
    }

    @Test
    void shouldRenderContactTypedStringExpression() throws IllegalVariableEvaluationException {
        TestVariableRenderer renderer = new TestVariableRenderer(applicationContext, variableConfiguration);
        Object render = renderer.renderTyped("{{ prefix }}.kestra.{{ suffix }}", Map.of("prefix", "io", "suffix", "unittest"));
        Assertions.assertEquals("io.kestra.unittest", render);
    }

    @Test
    void shouldRenderContactTypedNumberExpression() throws IllegalVariableEvaluationException {
        TestVariableRenderer renderer = new TestVariableRenderer(applicationContext, variableConfiguration);
        Object render = renderer.renderTyped("{{ prefix }}{{ suffix }}", Map.of("prefix", 10, "suffix", 42L));
        Assertions.assertEquals("1042", render);
    }

    @Test
    void shouldRenderTypedValueExpression() throws IllegalVariableEvaluationException {
        TestVariableRenderer renderer = new TestVariableRenderer(applicationContext, variableConfiguration);
        for (Object o : List.of(
            42,                         // Integer
            3.14,                       // Double
            true,                       // Boolean
            'x',                        // Character
            "hello",                    // String
            List.of(1, 2, 3),           // List
            Map.of("a", 1),      // Map
            new Object(),               // Arbitrary object
            new BigDecimal("123.45")  // BigDecimal
        )) {
            Object render = renderer.renderTyped("{{ input }}", Map.of("input", o));
            Assertions.assertEquals(o, render);
        }
    }

    @Test
    void shouldRenderStringAsNull() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ null }}", Map.of())).isNull();
        assertThat(variableRenderer.render("{{ true ? null : 'work' }}", Map.of())).isNull();
    }

    @Test
    void shouldRenderStringAsString() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ false ? null : 'work' }}", Map.of())).isEqualTo("work");
        assertThat(variableRenderer.render("{{ 42 }}", Map.of())).isEqualTo("42");
        assertThat(variableRenderer.render("{{ true }}", Map.of())).isEqualTo("true");
    }

    @Test
    void shouldRenderStringWithExplicitEmptyOutput() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ '' }}", Map.of())).isEqualTo("");
        assertThat(variableRenderer.render("prefix {{ null }}", Map.of())).isEqualTo("prefix ");
    }

    @Test
    void shouldRenderStringForComplexTypes() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ {'a': 1} }}", Map.of())).isEqualTo("{\"a\":1}");
        assertThat(variableRenderer.render("{{ [1, 2, 3] }}", Map.of())).isEqualTo("[1,2,3]");
    }

    @Test
    void shouldRenderStringForMixedComplexTemplate() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("prefix {{ {'a': 1} }}", Map.of()))
            .isEqualTo("prefix {\"a\":1}");
    }

    @Test
    void shouldKeepKeyOrderWhenRenderingMap() throws IllegalVariableEvaluationException {
        final Map<String, Object> input = new LinkedHashMap<>();
        input.put("foo-1", "A");
        input.put("foo-2", "B");

        final Map<String, Object> input_value3 = new LinkedHashMap<>();
        input_value3.put("bar-1", "C");
        input_value3.put("bar-2", "D");
        input_value3.put("bar-3", "E");
        //
        input.put("foo-3", input_value3);

        final Map<String, Object> result = variableRenderer.render(input, Map.of());
        assertThat(result.keySet()).containsExactly("foo-1", "foo-2", "foo-3");

        final Map<String, Object> result_value3 = (Map<String, Object>) result.get("foo-3");
        assertThat(result_value3.keySet()).containsExactly("bar-1", "bar-2", "bar-3");
    }

    public static class TestVariableRenderer extends VariableRenderer {

        public TestVariableRenderer(ApplicationContext applicationContext,
                                    VariableConfiguration variableConfiguration) {
            super(applicationContext, variableConfiguration);
        }

        @Override
        protected String alternativeRender(Exception e, String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
            return "result";
        }
    }


}
