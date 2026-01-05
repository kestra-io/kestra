package io.kestra.core.runners.pebble.functions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;

@MicronautTest
class FileURIFunctionTest {
    @Inject
    private VariableRenderer variableRenderer;

    @Test
    void fileURIFunction() throws IllegalVariableEvaluationException{
        String namespace = "my.namespace";
        String flowId = "flow";

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", flowId,
                "namespace", namespace),
            "fileA", "test"
        );
        String render = variableRenderer.render("{{ fileURI(fileA) }}", variables);
        assertThat(render).isEqualTo("kestra:///my/namespace/_files/test");
    }

    @Test
    void fileURIFunctionShouldThrowForIncorrectPath() throws IllegalVariableEvaluationException{
        String namespace = "my.namespace";
        String flowId = "flow";

        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "id", flowId,
                "namespace", namespace),
            "fileA", "../test"
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> variableRenderer.render("{{ fileURI(fileA) }}", variables));
        assertThat(exception.getMessage()).isEqualTo("Path must not contain '../'");
    }

}
