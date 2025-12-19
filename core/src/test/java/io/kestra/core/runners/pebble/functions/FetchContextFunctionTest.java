package io.kestra.core.runners.pebble.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class FetchContextFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void fromString() throws IllegalVariableEvaluationException, JsonProcessingException {
        String render = variableRenderer.render("{{ printContext() }}", Map.of("test", "value", "array", List.of("a", "b", "c")));
        assertThat(JacksonMapper.toMap(render).get("test")).isEqualTo("value");
        assertThat(JacksonMapper.toMap(render).get("array")).isEqualTo(List.of("a", "b", "c"));
    }
}
