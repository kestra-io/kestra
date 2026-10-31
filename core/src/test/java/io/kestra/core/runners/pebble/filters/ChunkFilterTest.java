package io.kestra.core.runners.pebble.filters;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.utils.Rethrow;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThrows(IllegalVariableEvaluationException.class, () ->
        {
            Rethrow.throwSupplier(() ->
            {
                variableRenderer.render("{{ test | chunk(2) }}", Map.of("test", 1));
                return null;
            }).get();
        });
    }

    @Test
    void chunkWithIntegerVariable() throws IllegalVariableEvaluationException {
        // Reproducer for issue: Integer variable causing ClassCastException
        Map<String, Object> vars = Map.of(
            "max_items", Integer.valueOf(2),
            "list", Arrays.asList(1, 2, 3, 4, 5)
        );

        String render = variableRenderer.render("{{ list | chunk(max_items) }}", vars);

        assertThat(render).isEqualTo("[[1,2],[3,4],[5]]");
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "1.5", "2147483648", "4294967297" })
    void shouldRejectInvalidSize(String size) {
        assertThatThrownBy(
            () -> variableRenderer.render(
                "{{ [1, 2, 3] | chunk(%s) }}".formatted(size),
                Map.of()
            )
        )
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining(
                "'chunk' filter argument 'size' must be an integer between 1 and 2147483647, inclusive."
            );
    }

    @Test
    void shouldAcceptMaximumSize() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render(
            "{{ [1, 2, 3] | chunk(2147483647) }}",
            Map.of()
        );

        assertThat(render).isEqualTo("[[1,2,3]]");
    }

}
