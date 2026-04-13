package io.kestra.core.runners.pebble.filters;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class UrlDecodeFilter {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void urldecode() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ 'Kestra rulez !' | urlencode | urldecode }}", Map.of());
        assertThat(render).isEqualTo("Kestra rulez !");
    }
}
