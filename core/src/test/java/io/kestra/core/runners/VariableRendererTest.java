package io.kestra.core.runners;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@KestraTest
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
        assertThat(result.keySet(), contains("foo-1", "foo-2", "foo-3"));

        final Map<String, Object> result_value3 = (Map<String, Object>) result.get("foo-3");
        assertThat(result_value3.keySet(), contains("bar-1", "bar-2", "bar-3"));
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