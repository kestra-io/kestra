package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@MicronautTest
class VariableRendererTest {

    @Inject
    ApplicationContext applicationContext;

    @Inject
    VariableRenderer.VariableConfiguration variableConfiguration;

    @Test
    void shouldRenderUsingAlternativeRendering() throws IllegalVariableEvaluationException {
        TestVariableRenderer renderer = new TestVariableRenderer(applicationContext, variableConfiguration);
        String render = renderer.render("{{ dummy }}", Map.of());
        Assertions.assertEquals("result", render);
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