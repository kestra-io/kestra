package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.configuration.VariableConfiguration;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.pebbletemplates.pebble.PebbleEngine;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class VariableRendererTest {
    @Inject
    VariableRenderer variableRenderer;

    @Inject
    VariableConfiguration variableConfiguration;

    @Inject
    private PebbleEngineFactory pebbleEngineFactory;

    @Test
    void shouldRenderContactUntypedStringExpression() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ prefix }}.kestra.{{ suffix }}", Map.of("prefix", "io", "suffix", "unittest"));
        Assertions.assertEquals("io.kestra.unittest", render);
    }

    @Test
    void shouldRenderContactTypedStringExpression() throws IllegalVariableEvaluationException {
        Object render = variableRenderer.renderTyped("{{ prefix }}.kestra.{{ suffix }}", Map.of("prefix", "io", "suffix", "unittest"));
        Assertions.assertEquals("io.kestra.unittest", render);
    }

    @Test
    void shouldRenderMixedTypeInString() throws IllegalVariableEvaluationException {
        Object render = variableRenderer.renderTyped("{\"a\": {{[1,2,3 ]}} }", Map.of());
        Assertions.assertEquals(Map.of("a", List.of(1, 2, 3)), render);
    }

    @Test
    void shouldRenderContactTypedNumberExpression() throws IllegalVariableEvaluationException {
        Object render = variableRenderer.renderTyped("{{ prefix }}{{ suffix }}", Map.of("prefix", 10, "suffix", 42L));
        Assertions.assertEquals("1042", render);
    }

    @Test
    void shouldRenderTypedValueExpression() throws IllegalVariableEvaluationException {
        TestVariableRenderer renderer = new TestVariableRenderer(pebbleEngineFactory, variableConfiguration);
        for (Object o : List.of(
            42, // Integer
            3.14, // Double
            true, // Boolean
            'x', // Character
            "hello", // String
            List.of(1, 2, 3), // List
            Map.of("a", 1), // Map
            new Object(), // Arbitrary object
            new BigDecimal("123.45") // BigDecimal
        )) {
            Object render = variableRenderer.renderTyped("{{ input }}", Map.of("input", o));
            Assertions.assertEquals(o, render);
        }
    }

    @Test
    void shouldWrapRuntimeExceptionInIllegalVariableEvaluationException() {
        // Given
        PebbleEngine mockEngine = Mockito.mock(PebbleEngine.class);
        Mockito.when(mockEngine.getLiteralTemplate(Mockito.anyString()))
            .thenThrow(new RuntimeException("unexpected runtime exception"));

        VariableRenderer renderer = new VariableRenderer(pebbleEngineFactory, variableConfiguration);
        renderer.setPebbleEngine(mockEngine);

        // When / Then
        assertThatThrownBy(() -> renderer.render("{{ test }}", Map.of()))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .cause()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("unexpected runtime exception");
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

    @Test
    void shouldRenderStringAsEmptyForNull() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ null }}", Map.of())).isEmpty();
        assertThat(variableRenderer.render("{{ true ? null : 'work' }}", Map.of())).isEmpty();
    }

    @Test
    void shouldRenderStringAsString() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ false ? null : 'work' }}", Map.of())).isEqualTo("work");
        assertThat(variableRenderer.render("{{ 42 }}", Map.of())).isEqualTo("42");
        assertThat(variableRenderer.render("{{ true }}", Map.of())).isEqualTo("true");
    }

    @Test
    void shouldRenderStringAsEmptyForNullRecursively() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ null }}", Map.of(), true)).isEmpty();
        assertThat(variableRenderer.render("prefix {{ null }}", Map.of(), true)).isEqualTo("prefix ");
    }

    @Test
    void shouldRenderStringWithExplicitEmptyOutput() throws IllegalVariableEvaluationException {
        assertThat(variableRenderer.render("{{ '' }}", Map.of())).isEqualTo("");
        assertThat(variableRenderer.render("prefix {{ null }}", Map.of())).isEqualTo("prefix ");
    }

    @Test
    void shouldRenderMapWithNullValues() throws IllegalVariableEvaluationException {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("key1", "{{ null }}");
        input.put("key2", "{{ 'hello' }}");
        input.put("key3", "static");
        Map<String, Object> result = variableRenderer.render(input, Map.of());
        assertThat(result.get("key1")).isNull();
        assertThat(result.get("key2")).isEqualTo("hello");
        assertThat(result.get("key3")).isEqualTo("static");
        assertThat(result).containsKey("key1");
    }

    @Test
    void shouldRenderMixedFilterAndOperator() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = Map.of("outputs", Map.of("after", Map.of("value", "300"), "before", Map.of("value", "250")));
        assertThat(variableRenderer.render("{{ outputs.after.value | number - outputs.before.value | number >= 5 }}", variables)).isEqualTo("true");
    }

    public static class TestVariableRenderer extends VariableRenderer {

        public TestVariableRenderer(PebbleEngineFactory pebbleEngineFactory,
            VariableConfiguration variableConfiguration) {
            super(pebbleEngineFactory, variableConfiguration);
        }
    }

}