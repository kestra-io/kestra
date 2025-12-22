package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@MicronautTest
class RenderOncerFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void noRenderNeeded() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ renderOnce(input) }}", Map.of("input", "test"));
        Assertions.assertEquals("test", rendered);
    }

    @Test
    void oneLayerRender() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ renderOnce(input) }}", Map.of("input", "{{someOtherVar}}", "someOtherVar", "test"));
        Assertions.assertEquals("test", rendered);
    }

    @Test
    void twoLayerRender() throws IllegalVariableEvaluationException {
        String rendered = variableRenderer.render("{{ renderOnce(input) }}", Map.of("input", "{{someOtherVar}}", "someOtherVar", "{{yetAnotherVar}}", "yetAnotherVar", "test"));
        Assertions.assertEquals("{{yetAnotherVar}}", rendered);
    }
}
