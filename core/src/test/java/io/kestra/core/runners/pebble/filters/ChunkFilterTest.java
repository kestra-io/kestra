package io.kestra.core.runners.pebble.filters;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.utils.Rethrow;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ChunkFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void out() throws IllegalVariableEvaluationException {
        Map<String, Object> vars = Map.of(
            "list", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9)
        );

        String render = variableRenderer.render("{{ list | chunk(2) }}", vars);

        assertThat(render).isEqualTo("[[1,2],[3,4],[5,6],[7,8],[9]]");
    }

    @Test
    void exception() {
        assertThrows(IllegalVariableEvaluationException.class, () -> {
            Rethrow.throwSupplier(() -> {
                variableRenderer.render("{{ test | chunk(2) }}", Map.of("test", 1));
                return null;
            }).get();
        });
    }
}